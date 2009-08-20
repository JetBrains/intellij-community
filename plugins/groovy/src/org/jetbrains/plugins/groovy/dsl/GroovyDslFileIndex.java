/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package org.jetbrains.plugins.groovy.dsl;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.util.containers.ConcurrentHashMap;
import com.intellij.util.indexing.*;
import com.intellij.util.io.EnumeratorStringDescriptor;
import com.intellij.util.io.KeyDescriptor;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;

import java.util.*;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * @author peter
 */
public class GroovyDslFileIndex extends ScalarIndexExtension<String> {
  private static final Logger LOG = Logger.getInstance("#org.jetbrains.plugins.groovy.dsl.GroovyDslFileIndex");
  @NonNls public static final ID<String, Void> NAME = ID.create("GroovyDslFileIndex");
  @NonNls private static final String OUR_KEY = "ourKey";
  private final MyDataIndexer myDataIndexer = new MyDataIndexer();
  private final MyInputFilter myInputFilter = new MyInputFilter();

  private static final Map<String, Pair<GroovyDslExecutor, Long>> ourMapping = new ConcurrentHashMap<String, Pair<GroovyDslExecutor, Long>>();

  private final EnumeratorStringDescriptor myKeyDescriptor = new EnumeratorStringDescriptor();

  public ID<String,Void> getName() {
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

  private static List<GroovyFile> getDslFiles(final GlobalSearchScope scope) {
    final Project project = scope.getProject();
    assert project != null;
    final Collection<VirtualFile> files = FileBasedIndex.getInstance().getContainingFiles(NAME, OUR_KEY, scope);
    if (files.isEmpty()) return Collections.emptyList();

    List<GroovyFile> result = new ArrayList<GroovyFile>();
    for(VirtualFile file: files) {
      if (!file.isValid()) continue;
      PsiFile psiFile = PsiManager.getInstance(project).findFile(file);
      if (psiFile instanceof GroovyFile) {
        result.add((GroovyFile)psiFile);
      }
    }
    return result;
  }

  @Nullable
  private static GroovyDslExecutor getCachedExecutor(@NotNull final VirtualFile file, final long stamp) {
    final Pair<GroovyDslExecutor, Long> pair = ourMapping.get(file.getUrl());
    if (pair == null || pair.second.longValue() != stamp) {
      return null;
    }
    return pair.first;
  }

  public static void processExecutors(PsiElement place, ClassDescriptor descriptor, GroovyEnhancerConsumer consumer) {
    final PsiFile placeFile = place.getContainingFile().getOriginalFile();
    final VirtualFile placeVFfile = placeFile.getVirtualFile();
    if (placeVFfile == null) {
      return;
    }

    int count = 0;
    final LinkedBlockingQueue<Pair<GroovyFile, GroovyDslExecutor>> queue = new LinkedBlockingQueue<Pair<GroovyFile, GroovyDslExecutor>>();

    final Set<String> unusedPaths = new THashSet<String>(ourMapping.keySet());
    for (final GroovyFile file : getDslFiles(new AdditionalIndexedRootsScope(place.getResolveScope(), StandardDslIndexedRootsProvider.class))) {
      final VirtualFile vfile = file.getVirtualFile();
      if (vfile == null) {
        continue;
      }
      unusedPaths.remove(vfile.getUrl());
      if (vfile.equals(placeVFfile)) {
        continue;
      }

      final long stamp = file.getModificationStamp();
      final GroovyDslExecutor cached = getCachedExecutor(vfile, stamp);
      if (cached == null) {
        final String text = file.getText();
        count++;
        scheduleParsing(queue, file, vfile, stamp, text);
      } else {
        cached.processVariants(descriptor, consumer);
      }
    }

    for (final String unusedPath : unusedPaths) {
      final VirtualFile file = VirtualFileManager.getInstance().findFileByUrl(unusedPath);
      if (file != null) {
        ourMapping.remove(unusedPath);
      }
    }

    try {
      while (count > 0) {
        ProgressManager.getInstance().checkCanceled();
        final Pair<GroovyFile, GroovyDslExecutor> pair = queue.poll(20, TimeUnit.MILLISECONDS);
        if (pair != null) {
          final GroovyDslExecutor executor = pair.second;
          if (executor != null) {
            executor.processVariants(descriptor, consumer);
          }

          count--;
        }
      }
    }
    catch (InterruptedException e) {
      LOG.error(e);
    }
  }

  private static void scheduleParsing(final LinkedBlockingQueue<Pair<GroovyFile, GroovyDslExecutor>> queue,
                                      final GroovyFile file,
                                      final VirtualFile vfile, final long stamp, final String text) {
    ApplicationManager.getApplication().executeOnPooledThread(new Runnable() {
      public void run() {
        try {
          synchronized (vfile) { //ensure that only one thread calculates dsl executor
            GroovyDslExecutor cached = getCachedExecutor(vfile, stamp);
            if (cached != null) {
              queue.offer(Pair.create(file, cached));
              return;
            }

            final GroovyDslExecutor executor = new GroovyDslExecutor(text, vfile.getName());

            // executor is not only time-consuming to create, but also takes some PermGenSpace
            // => we can't afford garbage-collecting it together with PsiFile
            // => cache globally by file path
            ourMapping.put(vfile.getUrl(), Pair.create(executor, stamp));
            queue.offer(Pair.create(file, executor));
          }
        }
        catch (Throwable e) {
          LOG.error(e);
          queue.offer(Pair.create(file, (GroovyDslExecutor)null));
        }
      }
    });
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

}
