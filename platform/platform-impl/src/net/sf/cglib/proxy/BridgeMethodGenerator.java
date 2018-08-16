// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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

  @Override
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

  @Override
  public void generateStatic(CodeEmitter e, CallbackGenerator.Context context, List methods) {
  }

}
