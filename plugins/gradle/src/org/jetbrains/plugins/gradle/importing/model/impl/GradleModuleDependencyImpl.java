package org.jetbrains.plugins.gradle.importing.model.impl;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.gradle.importing.model.GradleDependencyVisitor;
import org.jetbrains.plugins.gradle.importing.model.GradleModule;
import org.jetbrains.plugins.gradle.importing.model.GradleModuleDependency;

import java.io.Serializable;

/**
 * @author Denis Zhdanov
 * @since 8/10/11 6:40 PM
 */
public class GradleModuleDependencyImpl extends AbstractGradleDependency implements GradleModuleDependency, Serializable {

  private static final long serialVersionUID = 1L;
  
  private final GradleModule myModule;

  public GradleModuleDependencyImpl(@NotNull GradleModule module) {
    myModule = module;
  }

  @NotNull
  @Override
  public GradleModule getModule() {
    return myModule;
  }

  @Override
  public void invite(@NotNull GradleDependencyVisitor visitor) {
    visitor.visit(this); 
  }

  @Override
  public String toString() {
    return super.toString() + ", dependency module: " + getModule();
  }
}
