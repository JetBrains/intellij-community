// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.jps.devkit.threadingModelHelper;

import com.intellij.compiler.instrumentation.InstrumentationClassFinder;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.ModuleChunk;
import org.jetbrains.jps.incremental.BinaryContent;
import org.jetbrains.jps.incremental.CompileContext;
import org.jetbrains.jps.incremental.CompiledClass;
import org.jetbrains.jps.incremental.instrumentation.BaseInstrumentingBuilder;
import org.jetbrains.jps.incremental.messages.BuildMessage;
import org.jetbrains.jps.incremental.messages.CompilerMessage;
import org.jetbrains.org.objectweb.asm.ClassReader;
import org.jetbrains.org.objectweb.asm.ClassWriter;

import java.io.File;
import java.util.Collection;

public class TMHInstrumentingBuilder extends BaseInstrumentingBuilder {
  private static final Logger LOG = Logger.getInstance(TMHInstrumentingBuilder.class);

  public TMHInstrumentingBuilder() {
  }

  @NotNull
  @Override
  public String getPresentableName() {
    return "Threading Model annotations instrumentation";
  }

  @Override
  protected String getProgressMessage() {
    return "Adding Threading Model assertions...";
  }

  @Override
  protected boolean isEnabled(CompileContext context, ModuleChunk chunk) {
    return true;
  }

  @Override
  protected boolean canInstrument(CompiledClass compiledClass, int classFileVersion) {
    return !"module-info".equals(compiledClass.getClassName());
  }

  @Override
  @Nullable
  protected BinaryContent instrument(CompileContext context,
                                     CompiledClass compiledClass,
                                     ClassReader reader,
                                     ClassWriter writer,
                                     InstrumentationClassFinder finder) {
    try {
      if (TMHInstrumenter.instrument(reader, writer)) {
        return new BinaryContent(writer.toByteArray());
      }
    } catch (Throwable e) {
      LOG.error(e);
      final Collection<File> sourceFiles = compiledClass.getSourceFiles();
      String msg = "Cannot instrument " + ContainerUtil.map(sourceFiles, File::getName) + ": " + e.getMessage();
      String firstFile = ContainerUtil.getFirstItem(compiledClass.getSourceFilesPaths());
      context.processMessage(new CompilerMessage(getPresentableName(), BuildMessage.Kind.ERROR, msg, firstFile));
    }
    return null;
  }
}
