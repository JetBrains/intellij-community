/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.plugins.groovy.dsl;

import com.intellij.notification.Notification;
import com.intellij.notification.NotificationListener;
import com.intellij.notification.NotificationType;
import com.intellij.notification.Notifications;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.event.DocumentAdapter;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.newvfs.FileAttribute;
import com.intellij.psi.*;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.*;
import com.intellij.unscramble.UnscrambleDialog;
import com.intellij.util.containers.ConcurrentMultiMap;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.MultiMap;
import com.intellij.util.indexing.*;
import com.intellij.util.io.EnumeratorStringDescriptor;
import com.intellij.util.io.KeyDescriptor;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;

import javax.swing.event.HyperlinkEvent;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * @author peter
 */
public class GroovyDslFileIndex extends ScalarIndexExtension<String> {
  private static final Key<Pair<GroovyDslExecutor, Long>> CACHED_EXECUTOR = Key.create("CachedGdslExecutor");
  private static final Logger LOG = Logger.getInstance("#org.jetbrains.plugins.groovy.dsl.GroovyDslFileIndex");
  private static final FileAttribute ENABLED = new FileAttribute("ENABLED", 0);

  @NonNls public static final ID<String, Void> NAME = ID.create("GroovyDslFileIndex");
  @NonNls private static final String OUR_KEY = "ourKey";
  private final MyDataIndexer myDataIndexer = new MyDataIndexer();
  private final MyInputFilter myInputFilter = new MyInputFilter();

  private static final MultiMap<String, LinkedBlockingQueue<Pair<VirtualFile, GroovyDslExecutor>>> filesInProcessing =
    new ConcurrentMultiMap<String, LinkedBlockingQueue<Pair<VirtualFile, GroovyDslExecutor>>>();

  private static final ThreadPoolExecutor ourPool = new ThreadPoolExecutor(1, 1, 10, TimeUnit.SECONDS, new LinkedBlockingQueue<Runnable>());

  private final EnumeratorStringDescriptor myKeyDescriptor = new EnumeratorStringDescriptor();
  private static final byte[] ENABLED_FLAG = new byte[]{(byte)239};

  public ID<String, Void> getName() {
    return NAME;
  }

  public DataIndexer<String, Void, FileContent> getIndexer() {
    return myDataIndexer;
  }

  public KeyDescriptor<String> getKeyDescriptor() {
    return myKeyDescriptor;
  }

  public FileBasedIndex.InputFilter getInputFilter() {
    return myInputFilter;
  }

  public boolean dependsOnFileContent() {
    return false;
  }

  public int getVersion() {
    return 0;
  }

  public static boolean isActivated(VirtualFile file) {
    try {
      final byte[] bytes = ENABLED.readAttributeBytes(file);
      if (bytes == null) {
        return true;
      }

      return bytes.length == ENABLED_FLAG.length && bytes[0] == ENABLED_FLAG[0];
    }
    catch (IOException e) {
      return false;
    }
  }

  public static void activateUntilModification(final VirtualFile vfile) {
    final Document document = FileDocumentManager.getInstance().getDocument(vfile);
    if (document != null) {
      document.addDocumentListener(new DocumentAdapter() {
        @Override
        public void beforeDocumentChange(DocumentEvent e) {
          disableFile(vfile);
          document.removeDocumentListener(this);
        }
      });
    }

    try {
      ENABLED.writeAttributeBytes(vfile, ENABLED_FLAG);
    }
    catch (IOException e) {
      LOG.error(e);
    }
  }

  static void disableFile(final VirtualFile vfile) {
    try {
      ENABLED.writeAttributeBytes(vfile, new byte[0]);
    }
    catch (IOException e1) {
      LOG.error(e1);
    }
    vfile.putUserData(CACHED_EXECUTOR, null);
    for (Project project : ProjectManager.getInstance().getOpenProjects()) {
      project.putUserData(SCRIPTS_CACHE, null);
    }
  }


  @Nullable
  private static GroovyDslExecutor getCachedExecutor(@NotNull final VirtualFile file, final long stamp) {
    final Pair<GroovyDslExecutor, Long> pair = file.getUserData(CACHED_EXECUTOR);
    if (pair == null || pair.second.longValue() != stamp) {
      return null;
    }
    return pair.first;
  }

  public static boolean processExecutors(PsiClass psiClass, PsiElement place, PsiScopeProcessor processor) {
    if (!(place instanceof GrReferenceExpression) || PsiTreeUtil.getParentOfType(place, PsiAnnotation.class) != null) {
      // Basic filter, all DSL contexts are applicable for reference expressions only
      return true;
    }

    final String qname = psiClass.getQualifiedName();
    if (qname == null) {
      return true;
    }

    final PsiFile placeFile = place.getContainingFile().getOriginalFile();

    for (GroovyDslScript script : getDslScripts(place.getProject())) {
      if (!script.processExecutor(processor, psiClass, place, placeFile, qname)) {
        return false;
      }
    }

    return true;
  }

  private static final Key<CachedValue<List<GroovyDslScript>>> SCRIPTS_CACHE = Key.create("GdslScriptCache");
  private static List<GroovyDslScript> getDslScripts(final Project project) {
    return CachedValuesManager.getManager(project).getCachedValue(project, SCRIPTS_CACHE, new CachedValueProvider<List<GroovyDslScript>>() {
      @Override
      public Result<List<GroovyDslScript>> compute() {
        int count = 0;

        List<GroovyDslScript> result = new ArrayList<GroovyDslScript>();

        final LinkedBlockingQueue<Pair<VirtualFile, GroovyDslExecutor>> queue = new LinkedBlockingQueue<Pair<VirtualFile, GroovyDslExecutor>>();

        final GroovyDslIndexedRootProvider[] indexedRootProviders =
          ContainerUtil.findAllAsArray(IndexableSetContributor.EP_NAME.getExtensions(), GroovyDslIndexedRootProvider.class);
        final AdditionalIndexableFileSet standardSet = new AdditionalIndexableFileSet(indexedRootProviders);
        final ProjectFileIndex fileIndex = ProjectRootManager.getInstance(project).getFileIndex();

        final AdditionalIndexedRootsScope scope = new AdditionalIndexedRootsScope(GlobalSearchScope.allScope(project), standardSet);

        for (VirtualFile vfile : FileBasedIndex.getInstance().getContainingFiles(NAME, OUR_KEY, scope)) {
          if (!vfile.isValid()) {
            continue;
          }
          if (!standardSet.isInSet(vfile) && !fileIndex.isInLibraryClasses(vfile) && !fileIndex.isInLibrarySource(vfile)) {
            if (!fileIndex.isInSourceContent(vfile) || !isActivated(vfile)) {
              continue;
            }
          }

          final PsiFile psiFile = PsiManager.getInstance(project).findFile(vfile);
          if (psiFile == null) {
            continue;
          }

          final long stamp = vfile.getModificationStamp();
          final GroovyDslExecutor cached = getCachedExecutor(vfile, stamp);
          if (cached == null) {
            count++;
            scheduleParsing(queue, project, vfile, stamp, psiFile.getText());
          }
          else {
            result.add(new GroovyDslScript(project, vfile, cached));
          }
        }

        try {
          while (count > 0) {
            ProgressManager.checkCanceled();
            final Pair<VirtualFile, GroovyDslExecutor> pair = queue.poll(20, TimeUnit.MILLISECONDS);
            if (pair != null) {
              count--;
              if (pair.second != null) {
                result.add(new GroovyDslScript(project, pair.first, pair.second));
              }
            }
          }
        }
        catch (InterruptedException e) {
          LOG.error(e);
        }

        return Result.create(result, PsiModificationTracker.MODIFICATION_COUNT, ProjectRootManager.getInstance(project));
      }
    }, false);
  }

  private static class MyDataIndexer implements DataIndexer<String, Void, FileContent> {

    @NotNull
    public Map<String, Void> map(final FileContent inputData) {
      return Collections.singletonMap(OUR_KEY, null);
    }
  }

  private static class MyInputFilter implements FileBasedIndex.InputFilter {
    public boolean acceptInput(final VirtualFile file) {
      return "gdsl".equals(file.getExtension());
    }
  }

  private static void scheduleParsing(final LinkedBlockingQueue<Pair<VirtualFile, GroovyDslExecutor>> queue,
                                      final Project project,
                                      final VirtualFile vfile,
                                      final long stamp,
                                      final String text) {
    final String fileUrl = vfile.getUrl();

    final Runnable parseScript = new Runnable() {
      public void run() {
        GroovyDslExecutor executor = getCachedExecutor(vfile, stamp);
        if (executor == null) {
          executor = createExecutor(text, vfile, project);
          // executor is not only time-consuming to create, but also takes some PermGenSpace
          // => we can't afford garbage-collecting it together with PsiFile
          // => cache globally by file instance
          vfile.putUserData(CACHED_EXECUTOR, Pair.create(executor, stamp));
          if (executor != null) {
            activateUntilModification(vfile);
          }
        }

        // access to our MultiMap should be synchronized
        synchronized (vfile) {
          // put evaluated executor to all queues
          for (LinkedBlockingQueue<Pair<VirtualFile, GroovyDslExecutor>> queue : filesInProcessing.remove(fileUrl)) {
            queue.offer(Pair.create(vfile, executor));
          }
        }
      }
    };

    //noinspection SynchronizationOnLocalVariableOrMethodParameter
    synchronized (vfile) { //ensure that only one thread calculates dsl executor
      final boolean isNewRequest = !filesInProcessing.containsKey(fileUrl);
      filesInProcessing.putValue(fileUrl, queue);
      if (isNewRequest) {
        ourPool.execute(parseScript); //todo bring back multithreading when Groovy team fixes http://jira.codehaus.org/browse/GROOVY-4292
        //ApplicationManager.getApplication().executeOnPooledThread(parseScript);
      }
    }
  }

  private static boolean stopGdsl = false;

  @Nullable
  private static GroovyDslExecutor createExecutor(String text, VirtualFile vfile, final Project project) {
    if (stopGdsl) {
      return null;
    }

    try {
      return new GroovyDslExecutor(text, vfile.getName());
    }
    catch (final Throwable e) {
      if (project.isDisposed()) {
        LOG.error(e);
        return null;
      }

      if (ApplicationManager.getApplication().isUnitTestMode()) {
        LOG.error(e);
        return null;
      }
      invokeDslErrorPopup(e, project, vfile);

      if (e instanceof OutOfMemoryError) {
        stopGdsl = true;
        throw (Error)e;
      }
      if (e instanceof NoClassDefFoundError) {
        stopGdsl = true;
        throw (NoClassDefFoundError) e;
      }

      return null;
    }
  }
  static void invokeDslErrorPopup(Throwable e, final Project project, VirtualFile vfile) {
    if (!isActivated(vfile)) {
      return;
    }
    disableFile(vfile);

    final StringWriter writer = new StringWriter();
    //noinspection IOResourceOpenedButNotSafelyClosed
    e.printStackTrace(new PrintWriter(writer));
    final String exceptionText = writer.toString();
    LOG.info(exceptionText);

    ApplicationManager.getApplication().getMessageBus().syncPublisher(Notifications.TOPIC).notify(
      new Notification("Groovy DSL parsing", "DSL script execution error",
                       "<p>" + e.getMessage() + "</p><p><a href=\"\">Click here to investigate.</a></p>", NotificationType.ERROR,
                       new NotificationListener() {
                         public void hyperlinkUpdate(@NotNull Notification notification, @NotNull HyperlinkEvent event) {
                           final UnscrambleDialog dialog = new UnscrambleDialog(project);

                           dialog.setText(exceptionText);
                           dialog.show();
                           notification.expire();
                         }
                       }));
  }

}
