// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.plugins.gradle.tooling.builder;

import com.intellij.gradle.toolingExtension.util.GradleReflectionUtil;
import groovy.lang.MetaClass;
import groovy.lang.MetaMethod;
import org.codehaus.groovy.runtime.DefaultGroovyMethods;
import org.gradle.api.Action;
import org.gradle.api.file.CopySpec;
import org.gradle.api.file.FileTree;
import org.gradle.api.file.FileVisitDetails;
import org.gradle.api.file.FileVisitor;
import org.gradle.util.GradleVersion;
import org.jetbrains.annotations.ApiStatus;

import java.util.Collection;
import java.util.List;

/**
 * @author Vladislav.Soroka
 */
@SuppressWarnings("NullableProblems")
public final class CopySpecWalker {

  public interface Visitor {
    void visitSourcePath(String relativePath, String path);

    void visitDir(String relativePath, FileVisitDetails dirDetails);

    void visitFile(String relativePath, FileVisitDetails fileDetails);
  }

  private static Collection<Object> getSourcePaths(Object object) {
    MetaClass metaClass = DefaultGroovyMethods.getMetaClass(object);
    List<MetaMethod> getSourcePaths = metaClass.respondsTo(object, "getSourcePaths");
    if (!getSourcePaths.isEmpty()) {
      //noinspection unchecked,SSBasedInspection
      return (Collection<Object>)getSourcePaths.get(0).invoke(object, new Object[0]);
    }
    else if (metaClass.hasProperty(object, "sourcePaths") != null) {
      //noinspection unchecked
      return (Collection<Object>)metaClass.getProperty(object, "sourcePaths");
    }

    return null;
  }

  @SuppressWarnings("GrUnresolvedAccess")
  @ApiStatus.Internal
  public static void walk(CopySpec copySpec, Visitor visitor) {
    //if (true) return;

    copySpec.setIncludeEmptyDirs(true);

    MetaClass copySpecMetaclass = DefaultGroovyMethods.getMetaClass(copySpec);
    List<MetaMethod> walkMethods = copySpecMetaclass.respondsTo(copySpec, "walk", new Object[]{Action.class});
    if (walkMethods.isEmpty()) {
      return;
    }

    walkMethods.get(0).invoke(copySpec, new Object[]{new Action<Object>() {
      @Override
      public void execute(Object resolver) {
        // def resolver ->
        //      in Gradle v1.x - org.gradle.api.internal.file.copy.CopySpecInternal
        //      in Gradle v2.x - org.gradle.api.internal.file.copy.CopySpecResolver

        MetaClass resolverMetaclass = DefaultGroovyMethods.getMetaClass(resolver);

        List<MetaMethod> setIncludeEmptyDirs = resolverMetaclass.respondsTo(resolver, "setIncludeEmptyDirs", new Object[]{Boolean.class});
        if (!setIncludeEmptyDirs.isEmpty()) {
          setIncludeEmptyDirs.get(0).invoke(resolver, new Object[]{true});
        }

        if (resolverMetaclass.respondsTo(resolver, "getDestPath").isEmpty() ||
            resolverMetaclass.respondsTo(resolver, "getSource").isEmpty()) {
          throw new RuntimeException(GradleVersion.current() + " is not supported by JEE artifact importer");
        }

        @SuppressWarnings("SSBasedInspection")
        Object destPath = resolverMetaclass.respondsTo(resolver, "getDestPath").get(0).invoke(resolver, new Object[0]);

        final String relativePath = GradleReflectionUtil.getValue(destPath, "getPathString", String.class);

        Collection<Object> sourcePaths = getSourcePaths(resolver);
        if (sourcePaths == null) {
          Object this0 = resolverMetaclass.getProperty(resolver, "this$0");
          sourcePaths = getSourcePaths(this0);
        }

        if (sourcePaths != null) {
          for (Object path : DefaultGroovyMethods.flatten(sourcePaths)) {
            if (path instanceof String) {
              visitor.visitSourcePath(relativePath, (String)path);
            }
          }
        }

        FileTree sourceTree = GradleReflectionUtil.getValue(resolver, "getSource", FileTree.class);
        sourceTree.visit(new FileVisitor() {
          @Override
          public void visitDir(FileVisitDetails dirDetails) {
            try {
              visitor.visitDir(relativePath, dirDetails);
            }
            catch (Exception ignore) {
            }
          }

          @Override
          public void visitFile(FileVisitDetails fileDetails) {
            try {
              visitor.visitFile(relativePath, fileDetails);
            }
            catch (Exception ignore) {
            }
          }
        });
      }
    }});
  }
}
