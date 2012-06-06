package org.jetbrains.android.compiler.artifact;

import com.intellij.compiler.ant.Generator;
import com.intellij.facet.pointers.FacetPointer;
import com.intellij.facet.pointers.FacetPointersManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.JarFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.packaging.artifacts.ArtifactType;
import com.intellij.packaging.elements.*;
import com.intellij.packaging.impl.elements.FacetBasedPackagingElement;
import com.intellij.packaging.impl.elements.ModuleOutputPackagingElement;
import com.intellij.packaging.impl.ui.DelegatedPackagingElementPresentation;
import com.intellij.packaging.ui.ArtifactEditorContext;
import com.intellij.packaging.ui.PackagingElementPresentation;
import com.intellij.util.xmlb.annotations.Attribute;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.facet.AndroidRootUtil;
import org.jetbrains.android.util.AndroidCommonUtils;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * @author Eugene.Kudelevsky
 */
public class AndroidFinalPackageElement extends PackagingElement<AndroidFinalPackageElement.AndroidFinalPackageElementState>
  implements FacetBasedPackagingElement, ModuleOutputPackagingElement {

  @NonNls static final String FACET_ATTRIBUTE = "facet";

  private FacetPointer<AndroidFacet> myFacetPointer;
  private final Project myProject;

  public AndroidFinalPackageElement(@NotNull Project project, @Nullable AndroidFacet facet) {
    super(AndroidFinalPackageElementType.getInstance());
    myProject = project;
    myFacetPointer = facet != null ? FacetPointersManager.getInstance(myProject).create(facet) : null;
  }

  @Override
  public PackagingElementPresentation createPresentation(@NotNull ArtifactEditorContext context) {
    return new DelegatedPackagingElementPresentation(new AndroidFinalPackagePresentation(myFacetPointer));
  }

  @Nullable
  private String getApkPath() {
    if (myFacetPointer == null) {
      return null;
    }

    final AndroidFacet facet = myFacetPointer.getFacet();
    if (facet == null) {
      return null;
    }

    final String apkPath = AndroidRootUtil.getApkPath(facet);
    final String path = apkPath != null
                        ? AndroidCommonUtils.addSuffixToFileName(apkPath, AndroidCommonUtils.ANDROID_FINAL_PACKAGE_FOR_ARTIFACT_SUFFIX)
                        : null;
    return path != null
           ? FileUtil.toSystemIndependentName(path) + "!/"
           : null;
  }

  @Override
  public List<? extends Generator> computeAntInstructions(@NotNull PackagingElementResolvingContext resolvingContext,
                                                          @NotNull AntCopyInstructionCreator creator,
                                                          @NotNull ArtifactAntGenerationContext generationContext,
                                                          @NotNull ArtifactType artifactType) {
    final String apkPath = getApkPath();
    if (apkPath != null) {
      return Collections.singletonList(creator.createExtractedDirectoryInstruction(apkPath));
    }
    return Collections.emptyList();
  }

  @Override
  public void computeIncrementalCompilerInstructions(@NotNull IncrementalCompilerInstructionCreator creator,
                                                     @NotNull PackagingElementResolvingContext resolvingContext,
                                                     @NotNull ArtifactIncrementalCompilerContext compilerContext,
                                                     @NotNull ArtifactType artifactType) {
    final String apkPath = getApkPath();
    if (apkPath != null) {
      final VirtualFile apk = JarFileSystem.getInstance().findFileByPath(apkPath);
      if (apk != null && apk.isValid() && apk.isDirectory()) {
        creator.addDirectoryCopyInstructions(apk);
      }
    }
  }

  @Nullable
  public AndroidFacet getFacet() {
    return myFacetPointer != null ? myFacetPointer.getFacet() : null;
  }

  @Override
  public boolean isEqualTo(@NotNull PackagingElement<?> element) {
    if (!(element instanceof AndroidFinalPackageElement)) {
      return false;
    }
    final AndroidFinalPackageElement packageElement = (AndroidFinalPackageElement)element;

    return myFacetPointer == null
           ? packageElement.myFacetPointer == null
           : myFacetPointer.equals(packageElement.myFacetPointer);
  }

  public AndroidFinalPackageElementState getState() {
    final AndroidFinalPackageElementState state = new AndroidFinalPackageElementState();
    state.myFacetPointer = myFacetPointer != null ? myFacetPointer.getId() : null;
    return state;
  }

  @Override
  public AndroidFacet findFacet(@NotNull PackagingElementResolvingContext context) {
    return myFacetPointer != null ? myFacetPointer.findFacet(context.getModulesProvider(), context.getFacetsProvider()) : null;
  }

  public void loadState(AndroidFinalPackageElementState state) {
    myFacetPointer = state.myFacetPointer != null
                     ? FacetPointersManager.getInstance(myProject).<AndroidFacet>create(state.myFacetPointer)
                     : null;
  }

  @Override
  public String getModuleName() {
    return myFacetPointer != null ? myFacetPointer.getModuleName() : null;
  }

  @Override
  public Module findModule(PackagingElementResolvingContext context) {
    final AndroidFacet facet = findFacet(context);
    return facet != null ? facet.getModule() : null;
  }

  @NotNull
  @Override
  public Collection<VirtualFile> getSourceRoots(PackagingElementResolvingContext context) {
    return Collections.emptyList();
  }

  public static class AndroidFinalPackageElementState {

    @Attribute(FACET_ATTRIBUTE)
    public String myFacetPointer;
  }
}
