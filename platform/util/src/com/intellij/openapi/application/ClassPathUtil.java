// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.application;

import com.intellij.openapi.util.text.Strings;
import com.intellij.psi.codeStyle.MinusculeMatcher;
import com.intellij.util.XmlDomReader;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.xxh3.Xx3UnencodedString;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

@ApiStatus.Internal
public final class ClassPathUtil {
  private ClassPathUtil() {
  }

  public static @NotNull Collection<String> getUtilClassPath() {
    Class<?>[] classes = getUtilClasses();

    Set<String> classPath = new HashSet<>(classes.length);
    for (Class<?> aClass : classes) {
      String path = PathManager.getJarPathForClass(aClass);
      if (path != null) {
        classPath.add(path);
      }
    }

    addKotlinStdlib(classPath);
    return classPath;
  }

  public static void addKotlinStdlib(@NotNull Collection<String> classPath) {
    classPath.add(PathManager.getResourceRoot(PathManager.class, "/kotlin/jdk7/AutoCloseableKt.class")); // kotlin-stdlib-jdk7
    classPath.add(PathManager.getResourceRoot(PathManager.class, "/kotlin/streams/jdk8/StreamsKt.class")); // kotlin-stdlib-jdk8
  }

  public static @NotNull Class<?> @NotNull [] getUtilClasses() {
    @SuppressWarnings({"UnnecessaryFullyQualifiedName", "deprecation"}) Class<?>[] classes = {
      PathManager.class,                                  // module 'intellij.platform.util'
      Strings.class,                                      // module 'intellij.platform.util.base'
      XmlDomReader.class,                                 // module 'intellij.platform.util.xmlDom'
      MinusculeMatcher.class,                             // module 'intellij.platform.util.text.matching'
      com.intellij.openapi.util.SystemInfoRt.class,       // module 'intellij.platform.util.rt'
      com.intellij.diff.comparison.ComparisonUtil.class,  // module 'intellij.platform.util.diff'
      com.intellij.util.lang.UrlClassLoader.class,        // module 'intellij.platform.util.classLoader'
      Xx3UnencodedString.class,                           // intellij.platform.util.rt.java8 (required for classLoader)
      org.intellij.lang.annotations.Flow.class,           // jetbrains-annotations-java5
      org.jdom.Document.class,                            // jDOM
      org.apache.log4j.Appender.class,                    // Log4J
      it.unimi.dsi.fastutil.objects.Object2IntMap.class,  // fastutil
      gnu.trove.THashSet.class,                           // Trove,
      com.sun.jna.TypeMapper.class,                       // JNA
      com.sun.jna.platform.FileUtils.class,               // JNA (jna-platform)
      org.apache.oro.text.regex.PatternMatcher.class,     // OROMatcher
      net.jpountz.lz4.LZ4Factory.class,                   // LZ4-Java
      com.fasterxml.aalto.in.ReaderConfig.class,          // Aalto XML
      org.codehaus.stax2.XMLStreamReader2.class,          // Aalto XML
      kotlin.Pair.class, // kotlin-stdlib
    };
    return classes;
  }
}
