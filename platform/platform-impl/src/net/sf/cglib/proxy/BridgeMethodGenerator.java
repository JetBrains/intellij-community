/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package net.sf.cglib.proxy;

import net.sf.cglib.core.Block;
import net.sf.cglib.core.ClassEmitter;
import net.sf.cglib.core.CodeEmitter;
import net.sf.cglib.core.MethodInfo;

import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * @author Gregory.Shrago
 */
public class BridgeMethodGenerator implements CallbackGenerator {
  public static final InvocationHandlerGenerator INSTANCE = new InvocationHandlerGenerator();

  private final Map<MethodInfo, MethodInfo> myCovariantInfoMap;

  public BridgeMethodGenerator(final Map<MethodInfo, MethodInfo> covariantInfoMap) {
    myCovariantInfoMap = covariantInfoMap;
  }

  public void generate(ClassEmitter ce, CallbackGenerator.Context context, List methods) {
    for (Iterator it = methods.iterator(); it.hasNext();) {
      MethodInfo method = (MethodInfo)it.next();
      final MethodInfo delegate = myCovariantInfoMap.get(method);

      CodeEmitter e = context.beginMethod(ce, method);
      Block handler = e.begin_block();
      e.load_this();
      e.invoke_virtual_this(delegate.getSignature());
      e.return_value();
      handler.end();
      e.end_method();
    }
  }

  public void generateStatic(CodeEmitter e, CallbackGenerator.Context context, List methods) {
  }

}
