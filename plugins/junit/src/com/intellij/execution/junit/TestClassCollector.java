/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.execution.junit;

import com.intellij.execution.testframework.TestSearchScope;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.CompilerModuleExtension;
import com.intellij.openapi.roots.OrderEnumerator;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ArrayUtil;
import com.intellij.util.PathsList;
import com.intellij.util.lang.UrlClassLoader;

import java.io.File;
import java.io.IOException;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;

public class TestClassCollector {

  private static final Logger LOG = Logger.getInstance(TestClassCollector.class);

  public static String[] collectClassFQNames(String packageName, JUnitConfiguration configuration) {
    Module module = configuration.getConfigurationModule().getModule();
    List<URL> urls = new ArrayList<>();

    PathsList pathsList = (module == null ? OrderEnumerator.orderEntries(configuration.getProject()) : OrderEnumerator.orderEntries(module))
      .runtimeOnly().withoutSdk().recursively().getPathsList();
    for (VirtualFile file : pathsList.getVirtualFiles()) {
      try {
        urls.add(VfsUtilCore.virtualToIoFile(file).toURI().toURL());
      }
      catch (MalformedURLException ignored) {
        LOG.info(ignored);
      }
    }

    Path rootPath = null;
    if (configuration.getPersistentData().getScope() == TestSearchScope.SINGLE_MODULE) {
      CompilerModuleExtension moduleExtension = CompilerModuleExtension.getInstance(module);
      if (moduleExtension != null) {
        VirtualFile tests = moduleExtension.getCompilerOutputPathForTests();
        if (tests != null) {
          rootPath = Paths.get(VfsUtilCore.virtualToIoFile(tests).toURI());
        }
      }
    }

    Set<String> classes = new HashSet<>();
    UrlClassLoader classLoader = UrlClassLoader.build().allowLock().useCache().urls(urls).get();
    try {
      String packagePath = packageName.replace('.', '/');
      Enumeration<URL> resources = classLoader.getResources(packagePath);

      Class<?> testCaseClass = Class.forName("junit.framework.TestCase", true, classLoader);

      @SuppressWarnings("unchecked")
      Class<? extends Annotation> runWithClass = (Class<? extends Annotation>)Class.forName("org.junit.runner.RunWith", true, classLoader);

      @SuppressWarnings("unchecked")
      Class<? extends Annotation> testClass = (Class<? extends Annotation>)Class.forName("org.junit.Test", true, classLoader);

      while (resources.hasMoreElements()) {
        URL url = resources.nextElement();
        Path baseDir = Paths.get(url.toURI());

        //collect tests under single module test output only
        if (rootPath != null && !baseDir.startsWith(rootPath)) continue;

        Files.walkFileTree(baseDir, new SimpleFileVisitor<Path>() {
          @Override
          public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
            FileVisitResult result = super.visitFile(file, attrs);
            File f = file.toFile();
            String fName = f.getName();
            if (fName.endsWith(".class")) {
              try {
                Path relativePath = baseDir.relativize(file.getParent());
                String pathSeparator = baseDir.getFileSystem().getSeparator();
                String subpackageName = StringUtil.getQualifiedName(relativePath.toString().replace(pathSeparator, "."),
                                                                    FileUtil.getNameWithoutExtension(fName));
                String fqName = StringUtil.getQualifiedName(packageName, subpackageName);
                Class<?> aClass = Class.forName(fqName, false, classLoader);
                //is potential junit 4
                int modifiers = aClass.getModifiers();
                if (Modifier.isAbstract(modifiers) ||
                    !Modifier.isPublic(modifiers) ||
                   aClass.isMemberClass() && !Modifier.isStatic(modifiers)) {
                  return result;
                }
                //junit 3
                if (testCaseClass.isAssignableFrom(aClass)) {
                  classes.add(fqName);
                }
                else {
                  //annotation
                  if (aClass.isAnnotationPresent(runWithClass)) {
                    classes.add(fqName);
                  }
                  else {
                    //junit 4 & suite
                    for (Method method : aClass.getMethods()) {
                      if (Modifier.isStatic(method.getModifiers()) && "suite".equals(method.getName()) ||
                          method.isAnnotationPresent(testClass)) {
                        classes.add(fqName);
                        break;
                      }
                    }
                  }
                }
              }
              catch (Throwable e) {
                LOG.error(e);
              }
            }
            return result;
          }
        });
      }
    }
    catch (Throwable e) {
      LOG.error(e);
    }

    return ArrayUtil.toStringArray(classes);
  }
}
