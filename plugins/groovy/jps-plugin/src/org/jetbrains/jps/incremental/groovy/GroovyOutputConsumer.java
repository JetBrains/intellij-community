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
package org.jetbrains.jps.incremental.groovy;

import com.intellij.openapi.util.Ref;
import org.jetbrains.jps.builders.BuildTarget;
import org.jetbrains.jps.incremental.BinaryContent;
import org.jetbrains.jps.incremental.CompiledClass;
import org.jetbrains.jps.incremental.ModuleLevelBuilder;
import org.jetbrains.org.objectweb.asm.ClassReader;
import org.jetbrains.org.objectweb.asm.ClassVisitor;
import org.jetbrains.org.objectweb.asm.Opcodes;

import java.io.File;
import java.io.IOException;

/**
 * @author peter
 */
interface GroovyOutputConsumer {
  void registerCompiledClass(BuildTarget<?> target, File srcFile, File outputFile, byte[] bytes) throws IOException;
}

class DefaultOutputConsumer implements GroovyOutputConsumer {
  private final ModuleLevelBuilder.OutputConsumer myOutputConsumer;

  public DefaultOutputConsumer(ModuleLevelBuilder.OutputConsumer outputConsumer) {
    myOutputConsumer = outputConsumer;
  }

  @Override
  public void registerCompiledClass(BuildTarget<?> target, File srcFile, File outputFile, byte[] bytes) throws IOException {
    myOutputConsumer.registerCompiledClass(
      target,
      new CompiledClass(outputFile, srcFile, readClassName(bytes), new BinaryContent(bytes))
    );
  }

  private static String readClassName(byte[] classBytes) throws IOException{
    final Ref<String> nameRef = Ref.create(null);
    new ClassReader(classBytes).accept(new ClassVisitor(Opcodes.API_VERSION) {
      public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
        nameRef.set(name.replace('/', '.'));
      }
    }, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
    return nameRef.get();
  }

}
