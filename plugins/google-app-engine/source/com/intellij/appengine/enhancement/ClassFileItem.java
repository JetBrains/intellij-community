package com.intellij.appengine.enhancement;

import com.intellij.openapi.compiler.FileProcessingCompiler;
import com.intellij.openapi.compiler.ValidityState;
import com.intellij.openapi.compiler.TimestampValidityState;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.appengine.facet.AppEngineFacet;
import org.jetbrains.annotations.NotNull;

/**
 * @author nik
 */
public class ClassFileItem implements FileProcessingCompiler.ProcessingItem {
  private final VirtualFile myClassFile;
  private final VirtualFile mySourceFile;
  private final AppEngineFacet myFacet;

  public ClassFileItem(VirtualFile classFile, VirtualFile sourceFile, AppEngineFacet facet) {
    myClassFile = classFile;
    mySourceFile = sourceFile;
    myFacet = facet;
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
    return new TimestampValidityState(myClassFile.getTimeStamp());
  }
}
