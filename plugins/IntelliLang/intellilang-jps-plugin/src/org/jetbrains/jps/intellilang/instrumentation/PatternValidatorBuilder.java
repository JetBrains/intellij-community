/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package org.jetbrains.jps.intellilang.instrumentation;

import com.intellij.compiler.instrumentation.InstrumentationClassFinder;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.ModuleChunk;
import org.jetbrains.jps.incremental.BinaryContent;
import org.jetbrains.jps.incremental.CompileContext;
import org.jetbrains.jps.incremental.CompiledClass;
import org.jetbrains.jps.incremental.instrumentation.BaseInstrumentingBuilder;
import org.jetbrains.jps.incremental.messages.BuildMessage;
import org.jetbrains.jps.incremental.messages.CompilerMessage;
import org.jetbrains.jps.intellilang.model.InstrumentationException;
import org.jetbrains.jps.intellilang.model.JpsIntelliLangConfiguration;
import org.jetbrains.jps.intellilang.model.JpsIntelliLangExtensionService;
import org.jetbrains.org.objectweb.asm.ClassReader;
import org.jetbrains.org.objectweb.asm.ClassWriter;

/**
 * @author Eugene Zhuravlev
 */
public class PatternValidatorBuilder extends BaseInstrumentingBuilder{
  public PatternValidatorBuilder() {
  }

  @NotNull
  @Override
  public String getPresentableName() {
    return "IntelliLang Pattern Validator";
  }

  @Override
  protected boolean isEnabled(CompileContext context, ModuleChunk chunk) {
    final JpsIntelliLangConfiguration config = JpsIntelliLangExtensionService.getInstance().getConfiguration(context.getProjectDescriptor().getModel().getGlobal());
    return config.getInstrumentationType() != InstrumentationType.NONE;
  }

  @Override
  protected boolean canInstrument(CompiledClass compiledClass, int classFileVersion) {
    return !"module-info".equals(compiledClass.getClassName());
  }

  @Nullable
  @Override
  protected BinaryContent instrument(CompileContext context, CompiledClass compiled, ClassReader reader, ClassWriter writer, InstrumentationClassFinder finder) {
    final JpsIntelliLangConfiguration config =
      JpsIntelliLangExtensionService.getInstance().getConfiguration(context.getProjectDescriptor().getModel().getGlobal());
    final PatternInstrumenter instrumenter =
      new PatternInstrumenter(config.getPatternAnnotationClass(), writer, config.getInstrumentationType(), finder);
    try {
      reader.accept(instrumenter, 0);
      if (instrumenter.instrumented()) {
        return new BinaryContent(writer.toByteArray());
      }
    }
    catch (InstrumentationException e) {
      context.processMessage(new CompilerMessage(getPresentableName(), BuildMessage.Kind.ERROR, e.getMessage()));
    }
    return null;
  }

  @Override
  protected String getProgressMessage() {
    return "Adding pattern assertions...";
  }
}
