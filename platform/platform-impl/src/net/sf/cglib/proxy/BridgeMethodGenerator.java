// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package net.sf.cglib.proxy;

import net.sf.cglib.core.Block;
import net.sf.cglib.core.ClassEmitter;
import net.sf.cglib.core.CodeEmitter;
import net.sf.cglib.core.MethodInfo;

import java.util.List;
import java.util.Map;

/**
 * @author Gregory.Shrago
 */
public final class BridgeMethodGenerator implements CallbackGenerator {
  public static final InvocationHandlerGenerator INSTANCE = new InvocationHandlerGenerator();

  private final Map<MethodInfo, MethodInfo> myCovariantInfoMap;

  public BridgeMethodGenerator(final Map<MethodInfo, MethodInfo> covariantInfoMap) {
    myCovariantInfoMap = covariantInfoMap;
  }

  @Override
  public void generate(ClassEmitter ce, CallbackGenerator.Context context, List methods) {
    for (MethodInfo method : (List<MethodInfo>)methods) {
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
