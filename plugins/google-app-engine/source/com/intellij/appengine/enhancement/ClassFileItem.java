package com.intellij.appengine.enhancement;

import com.intellij.appengine.facet.AppEngineFacet;
import com.intellij.openapi.compiler.FileProcessingCompiler;
import com.intellij.openapi.compiler.ValidityState;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * @author nik
 */
public class ClassFileItem implements FileProcessingCompiler.ProcessingItem {
  private final VirtualFile myClassFile;
  private final VirtualFile mySourceFile;
  private final AppEngineFacet myFacet;
  private final List<VirtualFile> myDependencies;

  public ClassFileItem(VirtualFile classFile, VirtualFile sourceFile, AppEngineFacet facet, List<VirtualFile> dependencies) {
    myClassFile = classFile;
    mySourceFile = sourceFile;
    myFacet = facet;
    myDependencies = dependencies;
  }

  @NotNull
  public VirtualFile getFile() {
    return myClassFile;
  }

  public VirtualFile getSourceFile() {
    return mySourceFile;
  }

  public AppEngineFacet getFacet() {
    return myFacet;
  }

  public ValidityState getValidityState() {
    return new ClassFileItemDependenciesState(myDependencies);
  }

}
