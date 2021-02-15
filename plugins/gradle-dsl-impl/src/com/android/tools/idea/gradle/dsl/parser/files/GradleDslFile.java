/*
 * Copyright (C) 2017 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.tools.idea.gradle.dsl.parser.files;

import com.android.tools.idea.gradle.dsl.GradleUtil;
import com.android.tools.idea.gradle.dsl.api.BuildModelNotification;
import com.android.tools.idea.gradle.dsl.model.BuildModelContext;
import com.android.tools.idea.gradle.dsl.parser.GradleDslConverterFactory;
import com.android.tools.idea.gradle.dsl.parser.GradleDslParser;
import com.android.tools.idea.gradle.dsl.parser.GradleDslWriter;
import com.android.tools.idea.gradle.dsl.parser.android.AndroidDslElement;
import com.android.tools.idea.gradle.dsl.parser.apply.ApplyDslElement;
import com.android.tools.idea.gradle.dsl.parser.build.BuildScriptDslElement;
import com.android.tools.idea.gradle.dsl.parser.build.SubProjectsDslElement;
import com.android.tools.idea.gradle.dsl.parser.configurations.ConfigurationsDslElement;
import com.android.tools.idea.gradle.dsl.parser.dependencies.DependenciesDslElement;
import com.android.tools.idea.gradle.dsl.parser.elements.*;
import com.android.tools.idea.gradle.dsl.parser.ext.ExtDslElement;
import com.android.tools.idea.gradle.dsl.parser.java.JavaDslElement;
import com.android.tools.idea.gradle.dsl.parser.plugins.PluginsDslElement;
import com.android.tools.idea.gradle.dsl.parser.repositories.RepositoriesDslElement;
import com.android.tools.idea.gradle.dsl.parser.semantics.PropertiesElementDescription;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;

import java.io.File;
import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Stream;

import static com.android.tools.idea.gradle.dsl.parser.GradleDslConverterFactory.EXTENSION_POINT_NAME;
import static com.google.common.collect.ImmutableMap.toImmutableMap;
import static com.intellij.openapi.vfs.VfsUtilCore.virtualToIoFile;

/**
 * Provides Gradle specific abstraction over a {@link GroovyFile}.
 */
public abstract class GradleDslFile extends GradlePropertiesDslElement {
  private static final Logger LOG = Logger.getInstance(GradleDslFile.class);
  @NotNull private final ElementList myGlobalProperties = new ElementList();
  @NotNull private final VirtualFile myFile;
  @NotNull private final Project myProject;
  @NotNull private final Set<GradleDslFile> myChildModuleDslFiles = new HashSet<>();
  @NotNull private final GradleDslWriter myGradleDslWriter;
  @NotNull private final GradleDslParser myGradleDslParser;

  @Nullable private GradleDslFile myParentModuleDslFile;
  @Nullable private GradleDslFile mySiblingDslFile;

  @Nullable private ApplyDslElement myApplyDslElement;
  @NotNull private final BuildModelContext myBuildModelContext;

  public static final ImmutableMap<String, PropertiesElementDescription> CHILD_PROPERTIES_ELEMENTS_MAP = Stream.of(new Object[][]{
    {"android", AndroidDslElement.ANDROID},
    {"buildscript", BuildScriptDslElement.BUILDSCRIPT},
    {"configurations", ConfigurationsDslElement.CONFIGURATIONS},
    {"dependencies", DependenciesDslElement.DEPENDENCIES},
    {"ext", ExtDslElement.EXT},
    {"java", JavaDslElement.JAVA},
    {"repositories", RepositoriesDslElement.REPOSITORIES},
    {"subprojects", SubProjectsDslElement.SUBPROJECTS},
    {"plugins", PluginsDslElement.PLUGINS}
  }).collect(toImmutableMap(data -> (String) data[0], data -> (PropertiesElementDescription) data[1]));

  @NotNull
  @Override
  protected ImmutableMap<String, PropertiesElementDescription> getChildPropertiesElementsDescriptionMap() {
    return CHILD_PROPERTIES_ELEMENTS_MAP;
  }

  protected GradleDslFile(@NotNull VirtualFile file,
                          @NotNull Project project,
                          @NotNull String moduleName,
                          @NotNull BuildModelContext context) {
    super(null, null, GradleNameElement.fake(moduleName));
    myFile = file;
    myProject = project;
    myBuildModelContext = context;

    Application application = ApplicationManager.getApplication();
    PsiFile psiFile = application.runReadAction((Computable<PsiFile>)() -> PsiManager.getInstance(myProject).findFile(myFile));
    List<GradleDslConverterFactory> extensionList = EXTENSION_POINT_NAME.getExtensionList();

    GradleDslParser parser = null;
    GradleDslWriter writer = null;
    for(GradleDslConverterFactory factory: extensionList) {
      if (factory.canConvert(psiFile)) {
        parser = factory.createParser(psiFile, this);
        writer = factory.createWriter();
        setPsiElement(psiFile);
        break;
      }
    }

    if(parser !=null) {
      myGradleDslParser = parser;
      myGradleDslWriter = writer;
    } else {
      // If we don't support the language we ignore the PsiElement and set stubs for the writer and parser.
      // This means this file will produce an empty model.
      if(LOG.isDebugEnabled()) {
        LOG.debug("cannot find converter factory for " + psiFile.getName() + "(" + psiFile.getClass().getCanonicalName() + ")");
      }
      myGradleDslParser = new GradleDslParser.Adapter();
      myGradleDslWriter = new GradleDslWriter.Adapter();
    }
    populateGlobalProperties();
  }

  private void populateGlobalProperties() {
    GradleDslElement rootDir = new GradleDslGlobalValue(this, GradleUtil.getBaseDirPath(myProject).getPath(), "rootDir");
    myGlobalProperties.addElement(rootDir, ElementState.DEFAULT, false);
    GradleDslElement projectDir = new GradleDslGlobalValue(this, getDirectoryPath().getPath(), "projectDir");
    myGlobalProperties.addElement(projectDir, ElementState.DEFAULT, false);

    // org.gradle.api.JavaVersion
    ImmutableMap.Builder<String,String> builder = ImmutableMap.builder();
    Arrays.asList("1_1", "1_2", "1_3", "1_4", "1_5", "1_6", "1_7", "1_8", "1_9", "1_10", "11", "12", "13", "HIGHER")
      .forEach(s -> builder.put("VERSION_" + s, "JavaVersion.VERSION_" + s));
    Map<String,String> javaVersionValues = builder.build();
    GradleDslElement javaVersion = new GradleDslElementEnum(this, GradleNameElement.fake("JavaVersion"), javaVersionValues);
    myGlobalProperties.addElement(javaVersion, ElementState.DEFAULT, false);
  }

  @Override
  protected GradleDslElement getElementWhere(@NotNull Predicate<ElementList.ElementItem> predicate) {
    GradleDslElement result = super.getElementWhere(predicate);
    if (result == null) {
      result = myGlobalProperties.getElementWhere(predicate);
    }
    return result;
  }

  @Override
  protected GradleDslElement getElementBeforeChildWhere(Predicate<ElementList.ElementItem> predicate,
                                                        @NotNull GradleDslElement element,
                                                        boolean includeSelf) {
    GradleDslElement result = super.getElementBeforeChildWhere(predicate, element, includeSelf);
    if (result == null) {
      result = myGlobalProperties.getElementBeforeChildWhere(predicate, element, includeSelf);
    }
    return result;
  }

  /**
   * Parses the gradle file again. This is a convenience method when an already parsed gradle file needs to be parsed again
   * (for example, after making changes to the PSI elements.)
   */
  public void reparse() {
    clear();
    parse();
  }

  public void parse() {
    myGradleDslParser.parse();
    // we might have textually-forward references that are nevertheless valid because of the prioritization of the buildscript block:
    // attempt resolution once more after the whole of the file is parsed.
    getContext().getDependencyManager().resolveAllIn(this, true);
  }

  @NotNull
  public Project getProject() {
    return myProject;
  }

  @NotNull
  public VirtualFile getFile() {
    return myFile;
  }

  @NotNull
  public File getDirectoryPath() {
    return virtualToIoFile(getFile().getParent());
  }

  @NotNull
  public List<GradleDslFile> getApplyDslElement() {
    return myApplyDslElement == null ? ImmutableList.of() : myApplyDslElement.getAppliedDslFiles();
  }

  public void setParentModuleDslFile(@NotNull GradleDslFile parentModuleDslFile) {
    myParentModuleDslFile = parentModuleDslFile;
    myParentModuleDslFile.myChildModuleDslFiles.add(this);
  }

  @Nullable
  public GradleDslFile getParentModuleDslFile() {
    return myParentModuleDslFile;
  }

  @NotNull
  public Collection<GradleDslFile> getChildModuleDslFiles() {
    return myChildModuleDslFiles;
  }

  /**
   * Sets the sibling dsl file of this file.
   *
   * <p>build.gradle and gradle.properties files belongs to the same module are considered as sibling files.
   */
  public void setSiblingDslFile(@NotNull GradleDslFile siblingDslFile) {
    mySiblingDslFile = siblingDslFile;
  }

  /**
   * Returns the sibling dsl file of this file.
   *
   * <p>build.gradle and gradle.properties files belongs to the same module are considered as sibling files.
   */
  @Nullable
  public GradleDslFile getSiblingDslFile() {
    return mySiblingDslFile;
  }

  @NotNull
  public GradleDslWriter getWriter() {
    return myGradleDslWriter;
  }

  @NotNull
  public GradleDslParser getParser() {
    return myGradleDslParser;
  }

  @NotNull
  public BuildModelContext getContext() {
    return myBuildModelContext;
  }

  @Override
  protected void apply() {
    // First make sure we update all our applied files.
    if (myApplyDslElement != null) {
      for (GradleDslFile file : myApplyDslElement.getAppliedDslFiles()) {
        file.apply();
      }
    }

    // And update us.
    super.apply();
  }

  public void registerApplyElement(@NotNull ApplyDslElement applyElement) {
    myApplyDslElement = applyElement;
  }

  @NotNull
  public List<BuildModelNotification> getPublicNotifications() {
    return myBuildModelContext.getPublicNotifications(this);
  }

  public void saveAllChanges() {
    PsiElement element = getPsiElement();
    // Properties files do not have PsiElements.
    if (element == null) {
      return;
    }

    // Check for any postponed psi operations and complete them to unblock the underlying document for further modifications.
    assert element instanceof PsiFile;

    PsiDocumentManager psiDocumentManager = PsiDocumentManager.getInstance(getProject());
    Document document = psiDocumentManager.getDocument((PsiFile)element);
    if (document == null) {
      return;
    }

    if (psiDocumentManager.isDocumentBlockedByPsi(document)) {
      psiDocumentManager.doPostponedOperationsAndUnblockDocument(document);
    }

    // Save the file to disk to ensure the changes exist when it is read.
    FileDocumentManager.getInstance().saveDocument(document);
    // Saving can alter the document, for example if any trailing spaces were present and were removed on save.
    if (!psiDocumentManager.isCommitted(document)) {
      psiDocumentManager.commitDocument(document);
    }
  }

  @Nullable
  public VirtualFile tryToFindSettingsFile() {
    if (this instanceof GradleSettingsFile) {
      return getFile();
    }

    VirtualFile buildFileParent = getFile().getParent();
    while (buildFileParent != null) {
      VirtualFile maybeSettingsFile = myBuildModelContext.getGradleSettingsFile(virtualToIoFile(buildFileParent));
      if (maybeSettingsFile != null) {
        return maybeSettingsFile;
      }
      buildFileParent = buildFileParent.getParent();
    }
    return null;
  }

  public boolean isKotlin() {
    return myGradleDslParser.isKotlin();
  }

}
