// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.java.decompiler.roundTrip.fixtures;

import groovy.lang.GroovyClassLoader;
import org.codehaus.groovy.control.CompilationUnit;
import org.codehaus.groovy.control.CompilerConfiguration;
import org.codehaus.groovy.control.Phases;
import org.codehaus.groovy.tools.GroovyClass;
import org.eclipse.jdt.internal.compiler.tool.EclipseCompiler;

import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;
import java.io.IOException;
import java.io.StringWriter;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public enum Compiler {
  JAVAC("javac", ".java") {
    @Override
    public Map<String, byte[]> compile(Set<Path> sourceFiles) {
      return compileJava(ToolProvider.getSystemJavaCompiler(), sourceFiles);
    }
  },
  ECJ("ecj", ".java") {
    @Override
    public Map<String, byte[]> compile(Set<Path> sourceFiles) {
      return compileJava(new EclipseCompiler(), sourceFiles);
    }
  },
  GROOVYC("groovyc", ".groovy") {
    @Override
    public Map<String, byte[]> compile(Set<Path> sourceFiles) {
      return compileGroovy(sourceFiles);
    }
  };

  private final String id;
  private final String sourceExtension;

  Compiler(String id, String sourceExtension) {
    this.id = id;
    this.sourceExtension = sourceExtension;
  }

  public abstract Map<String, byte[]> compile(Set<Path> sourceFiles);

  public String getId() {
    return id;
  }

  public String getSourceExtension() {
    return sourceExtension;
  }

  private static Map<String, byte[]> compileJava(JavaCompiler javaCompiler, Set<Path> sourceFiles) {
    StringWriter errors = new StringWriter();
    StandardJavaFileManager standardFM = javaCompiler.getStandardFileManager(null, null, StandardCharsets.UTF_8);
    try (InMemoryFileManager memFM = new InMemoryFileManager(standardFM)) {
      List<String> compilerOptions = List.of("-g");
      Iterable<? extends JavaFileObject> units = standardFM.getJavaFileObjectsFromPaths(sourceFiles);
      JavaCompiler.CompilationTask task = javaCompiler.getTask(errors, memFM, null, compilerOptions, null, units);
      if (!task.call()) {
        throw new AssertionError("Java compilation failed:\n" + errors);
      }
      return memFM.getClassBytes();
    }
    catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  private static Map<String, byte[]> compileGroovy(Set<Path> sourceFiles) {
    CompilerConfiguration config = new CompilerConfiguration();
    GroovyClassLoader classLoader = new GroovyClassLoader(Compiler.class.getClassLoader(), config);
    CompilationUnit unit = new CompilationUnit(config, null, classLoader);
    for (Path src : sourceFiles) {
      //noinspection IO_FILE_USAGE
      unit.addSource(src.toFile());
    }
    unit.compile(Phases.CLASS_GENERATION);

    Map<String, byte[]> result = new LinkedHashMap<>();
    for (GroovyClass gc : unit.getClasses()) {
      result.put(gc.getName().replace('.', '/'), gc.getBytes());
    }
    return result;
  }
}
