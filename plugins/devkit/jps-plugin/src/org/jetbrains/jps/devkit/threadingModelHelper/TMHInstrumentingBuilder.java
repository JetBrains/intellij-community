// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.devkit.threadingModelHelper;

import com.intellij.compiler.instrumentation.InstrumentationClassFinder;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.SystemProperties;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.ModuleChunk;
import org.jetbrains.jps.cmdline.ProjectDescriptor;
import org.jetbrains.jps.devkit.DevKitJpsBundle;
import org.jetbrains.jps.incremental.BinaryContent;
import org.jetbrains.jps.incremental.CompileContext;
import org.jetbrains.jps.incremental.CompiledClass;
import org.jetbrains.jps.incremental.JvmClassFileInstrumenter;
import org.jetbrains.jps.incremental.instrumentation.BaseInstrumentingBuilder;
import org.jetbrains.jps.incremental.messages.BuildMessage;
import org.jetbrains.jps.incremental.messages.CompilerMessage;
import org.jetbrains.jps.model.module.JpsModule;
import org.jetbrains.org.objectweb.asm.ClassReader;
import org.jetbrains.org.objectweb.asm.ClassWriter;

import java.io.File;
import java.io.IOException;
import java.util.Collection;

public final class TMHInstrumentingBuilder extends BaseInstrumentingBuilder implements JvmClassFileInstrumenter {
  private static final Logger LOG = Logger.getInstance(TMHInstrumentingBuilder.class);
  public static final String INSTRUMENT_ANNOTATIONS_PROPERTY = "tmh.instrument.annotations";
  static final String GENERATE_LINE_NUMBERS_PROPERTY = "tmh.generate.line.numbers";

  public TMHInstrumentingBuilder() {
  }

  @Override
  public @NotNull String getId() {
    return "devkit-threading-assertions";
  }

  @Override
  public boolean isEnabled(@NotNull ProjectDescriptor projectDescriptor, @NotNull JpsModule module) {
    return SystemProperties.getBooleanProperty(INSTRUMENT_ANNOTATIONS_PROPERTY, false);
  }

  @Override
  public int getVersion() {
    return 0;
  }

  @Override
  public @NotNull String getPresentableName() {
    return DevKitJpsBundle.message("tmh.instrumenting.builder.name");
  }

  @Override
  protected String getProgressMessage() {
    return DevKitJpsBundle.message("tmh.instrumenting.builder.progress");
  }

  @Override
  protected boolean isEnabled(CompileContext context, ModuleChunk chunk) {
    return SystemProperties.getBooleanProperty(INSTRUMENT_ANNOTATIONS_PROPERTY, false) ||
           Boolean.TRUE.toString().equals(context.getBuilderParameter(INSTRUMENT_ANNOTATIONS_PROPERTY));
  }

  @Override
  protected boolean canInstrument(CompiledClass compiledClass, int classFileVersion) {
    return !"module-info".equals(compiledClass.getClassName());
  }

  @Override
  protected @Nullable BinaryContent instrument(CompileContext context,
                                               CompiledClass compiledClass,
                                               ClassReader reader,
                                               ClassWriter writer,
                                               InstrumentationClassFinder finder) {
    try {
      boolean generateLineNumbers = SystemProperties.getBooleanProperty(GENERATE_LINE_NUMBERS_PROPERTY, false);
      var generators = hasThreadingAssertions(finder) ? TMHAssertionGenerator2.generators()
                                                      : TMHAssertionGenerator1.generators();
      if (TMHInstrumenter.instrument(reader, writer, generators, generateLineNumbers)) {
        return new BinaryContent(writer.toByteArray());
      }
    } catch (Throwable e) {
      LOG.error(e);
      final Collection<File> sourceFiles = compiledClass.getSourceFiles();
      String msg = DevKitJpsBundle.message("tmh.cannot.instrument", StringUtil.join(sourceFiles, File::getName, ", "), e.getMessage());
      String firstFile = ContainerUtil.getFirstItem(compiledClass.getSourceFilesPaths());
      context.processMessage(new CompilerMessage(getPresentableName(), BuildMessage.Kind.ERROR, msg, firstFile));
    }
    return null;
  }

  private static boolean hasThreadingAssertions(InstrumentationClassFinder finder) {
    try {
      finder.loadClass("com/intellij/util/concurrency/ThreadingAssertions");
      return true;
    }
    catch (IOException | ClassNotFoundException e) {
      return false;
    }
  }
}
