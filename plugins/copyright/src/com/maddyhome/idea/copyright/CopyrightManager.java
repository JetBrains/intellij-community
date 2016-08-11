/*
 * Copyright 2000-2016 JetBrains s.r.o.
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

package com.maddyhome.idea.copyright;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.components.*;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.event.DocumentAdapter;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.event.DocumentListener;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.startup.StartupManager;
import com.intellij.openapi.util.*;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.packageDependencies.DependencyValidationManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.search.scope.packageSet.NamedScope;
import com.intellij.psi.search.scope.packageSet.PackageSet;
import com.maddyhome.idea.copyright.actions.UpdateCopyrightProcessor;
import com.maddyhome.idea.copyright.options.LanguageOptions;
import com.maddyhome.idea.copyright.options.Options;
import com.maddyhome.idea.copyright.util.FileTypeUtil;
import com.maddyhome.idea.copyright.util.NewFileTracker;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

@State(name = "CopyrightManager", storages = @Storage(value = "copyright", stateSplitter = CopyrightManager.CopyrightStateSplitter.class))
public class CopyrightManager extends AbstractProjectComponent implements PersistentStateComponent<Element> {
  private static final Logger LOG = Logger.getInstance("#" + CopyrightManager.class.getName());
  @Nullable
  private CopyrightProfile myDefaultCopyright = null;
  private final LinkedHashMap<String, String> myModuleToCopyrights = new LinkedHashMap<>();
  private final Map<String, CopyrightProfile> myCopyrights = new TreeMap<>();
  private final Options myOptions = new Options();

  public CopyrightManager(@NotNull Project project,
                          @NotNull final EditorFactory editorFactory,
                          @NotNull final Application application,
                          @NotNull final FileDocumentManager fileDocumentManager,
                          @NotNull final FileTypeUtil fileTypeUtil,
                          @NotNull final ProjectRootManager projectRootManager,
                          @NotNull final PsiManager psiManager,
                          @NotNull StartupManager startupManager) {
    super(project);
    if (!myProject.isDefault()) {
      final NewFileTracker newFileTracker = NewFileTracker.getInstance();
      Disposer.register(myProject, new Disposable() {
        @Override
        public void dispose() {
          newFileTracker.clear();
        }
      });
      startupManager.runWhenProjectIsInitialized(() -> {
        DocumentListener listener = new DocumentAdapter() {
          @Override
          public void documentChanged(DocumentEvent e) {
            final Document document = e.getDocument();
            final VirtualFile virtualFile = fileDocumentManager.getFile(document);
            if (virtualFile == null) return;
            final Module module = projectRootManager.getFileIndex().getModuleForFile(virtualFile);
            if (module == null) return;
            if (!newFileTracker.poll(virtualFile)) return;
            if (!fileTypeUtil.isSupportedFile(virtualFile)) return;
            if (psiManager.findFile(virtualFile) == null) return;
            application.invokeLater(() -> {
              if (!virtualFile.isValid()) return;
              final PsiFile file = psiManager.findFile(virtualFile);
              if (file != null && file.isWritable()) {
                final CopyrightProfile opts = getCopyrightOptions(file);
                if (opts != null) {
                  new UpdateCopyrightProcessor(myProject, module, file).run();
                }
              }
            }, ModalityState.NON_MODAL, myProject.getDisposed());
          }
        };
        editorFactory.getEventMulticaster().addDocumentListener(listener, myProject);
      });
    }
  }

  @NonNls
  static final String COPYRIGHT = "copyright";
  @NonNls
  private static final String MODULE2COPYRIGHT = "module2copyright";
  @NonNls
  private static final String ELEMENT = "element";
  @NonNls
  private static final String MODULE = "module";
  @NonNls
  private static final String DEFAULT = "default";

  public static CopyrightManager getInstance(Project project) {
    return project.getComponent(CopyrightManager.class);
  }

  @Override
  @NonNls
  @NotNull
  public String getComponentName() {
    return "CopyrightManager";
  }

  @Override
  public Element getState() {
    Element state = new Element("settings");

    try {
      if (!myCopyrights.isEmpty()) {
        for (CopyrightProfile copyright : myCopyrights.values()) {
          Element copyrightElement = new Element(COPYRIGHT);
          copyright.serializeInto(copyrightElement, true);
          if (!JDOMUtil.isEmpty(copyrightElement)) {
            state.addContent(copyrightElement);
          }
        }
      }

      if (!myModuleToCopyrights.isEmpty()) {
        final Element map = new Element(MODULE2COPYRIGHT);
        for (String moduleName : myModuleToCopyrights.keySet()) {
          final Element setting = new Element(ELEMENT);
          setting.setAttribute(MODULE, moduleName);
          setting.setAttribute(COPYRIGHT, myModuleToCopyrights.get(moduleName));
          map.addContent(setting);
        }
        state.addContent(map);
      }

      myOptions.writeExternal(state);
    }
    catch (WriteExternalException e) {
      LOG.error(e);
      return null;
    }

    if (myDefaultCopyright != null) {
      state.setAttribute(DEFAULT, myDefaultCopyright.getName());
    }
    else if (!myProject.isDefault()) {
      // todo we still add empty attribute to avoid annoying change (idea 12 - attribute exists, idea 13 - attribute doesn't exists)
      // CR-IC-3403#CFR-62470, idea <= 12 compatibility
      state.setAttribute(DEFAULT, "");
    }

    return state;
  }

  @Override
  public void loadState(Element state) {
    clearCopyrights();

    final Element moduleToCopyright = state.getChild(MODULE2COPYRIGHT);
    if (moduleToCopyright != null) {
      for (Element element : moduleToCopyright.getChildren(ELEMENT)) {
        myModuleToCopyrights.put(element.getAttributeValue(MODULE), element.getAttributeValue(COPYRIGHT));
      }
    }

    try {
      for (Element element : state.getChildren(COPYRIGHT)) {
        final CopyrightProfile copyrightProfile = new CopyrightProfile();
        copyrightProfile.readExternal(element);
        myCopyrights.put(copyrightProfile.getName(), copyrightProfile);
      }
      myDefaultCopyright = myCopyrights.get(StringUtil.notNullize(state.getAttributeValue(DEFAULT)));
      myOptions.readExternal(state);
    }
    catch (InvalidDataException e) {
      LOG.error(e);
    }
  }

  public Map<String, String> getCopyrightsMapping() {
    return myModuleToCopyrights;
  }

  public void setDefaultCopyright(@Nullable CopyrightProfile copyright) {
    myDefaultCopyright = copyright;
  }

  @Nullable
  public CopyrightProfile getDefaultCopyright() {
    return myDefaultCopyright;
  }

  public void addCopyright(CopyrightProfile copyrightProfile) {
    myCopyrights.put(copyrightProfile.getName(), copyrightProfile);
  }

  public void removeCopyright(CopyrightProfile copyrightProfile) {
    myCopyrights.values().remove(copyrightProfile);
    for (Iterator<String> it = myModuleToCopyrights.keySet().iterator(); it.hasNext();) {
      final String profileName = myModuleToCopyrights.get(it.next());
      if (profileName.equals(copyrightProfile.getName())) {
        it.remove();
      }
    }
  }

  public void clearCopyrights() {
    myDefaultCopyright = null;
    myCopyrights.clear();
    myModuleToCopyrights.clear();
  }

  public void mapCopyright(String scopeName, String copyrightProfileName) {
    myModuleToCopyrights.put(scopeName, copyrightProfileName);
  }

  public void unmapCopyright(String scopeName) {
    myModuleToCopyrights.remove(scopeName);
  }

  public Collection<CopyrightProfile> getCopyrights() {
    return myCopyrights.values();
  }

  public boolean hasAnyCopyrights() {
    return myDefaultCopyright != null || !myModuleToCopyrights.isEmpty();
  }

  @Nullable
  public CopyrightProfile getCopyrightOptions(@NotNull PsiFile file) {
    final VirtualFile virtualFile = file.getVirtualFile();
    if (virtualFile == null || myOptions.getOptions(virtualFile.getFileType().getName()).getFileTypeOverride() == LanguageOptions.NO_COPYRIGHT) return null;
    final DependencyValidationManager validationManager = DependencyValidationManager.getInstance(myProject);
    for (String scopeName : myModuleToCopyrights.keySet()) {
      final NamedScope namedScope = validationManager.getScope(scopeName);
      if (namedScope != null) {
        final PackageSet packageSet = namedScope.getValue();
        if (packageSet != null) {
          if (packageSet.contains(file, validationManager)) {
            final CopyrightProfile profile = myCopyrights.get(myModuleToCopyrights.get(scopeName));
            if (profile != null) {
              return profile;
            }
          }
        }
      }
    }
    return myDefaultCopyright != null ? myDefaultCopyright : null;
  }

  public Options getOptions() {
    return myOptions;
  }

  public void replaceCopyright(String displayName, CopyrightProfile copyrightProfile) {
    if (myDefaultCopyright != null && Comparing.strEqual(myDefaultCopyright.getName(), displayName)) {
      myDefaultCopyright = copyrightProfile;
    }
    myCopyrights.remove(displayName);
    addCopyright(copyrightProfile);
  }

  static final class CopyrightStateSplitter extends MainConfigurationStateSplitter {
    @NotNull
    @Override
    protected String getComponentStateFileName() {
      return "profiles_settings";
    }

    @NotNull
    @Override
    protected String getSubStateTagName() {
      return COPYRIGHT;
    }
  }
}
