package com.intellij.appengine.enhancement;

import com.intellij.appengine.facet.AppEngineFacet;
import com.intellij.facet.pointers.FacetPointersManager;
import com.intellij.openapi.compiler.generic.BuildTarget;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

/**
 * @author nik
 */
public class EnhancementTarget extends BuildTarget {
  private AppEngineFacet myFacet;
  private final VirtualFile myOutputRoot;

  public EnhancementTarget(@NotNull AppEngineFacet facet, @NotNull VirtualFile outputRoot) {
    myFacet = facet;
    myOutputRoot = outputRoot;
  }

  @NotNull
  public AppEngineFacet getFacet() {
    return myFacet;
  }

  @NotNull
  public VirtualFile getOutputRoot() {
    return myOutputRoot;
  }

  @NotNull
  @Override
  public String getId() {
    return FacetPointersManager.constructId(myFacet);
  }
}
