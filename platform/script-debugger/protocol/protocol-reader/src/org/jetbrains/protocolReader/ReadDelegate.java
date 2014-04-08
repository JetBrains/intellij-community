package org.jetbrains.protocolReader;

import java.lang.reflect.Method;
import java.util.Collections;
import java.util.List;

class ReadDelegate {
  private static final List<String> STATIC_METHOD_PARAM_NAME_LIST = Collections.singletonList(Util.READER_NAME);

  private final TypeHandler<?> typeHandler;
  private final boolean isList;

  ReadDelegate(TypeHandler<?> typeHandler, boolean isList) {
    this.typeHandler = typeHandler;
    this.isList = isList;
  }

  void write(ClassScope scope, Method method, TextOutput out) {
    MethodHandler.writeMethodDeclarationJava(out, method, STATIC_METHOD_PARAM_NAME_LIST);
    out.openBlock();
    out.append("return ");
    if (isList) {
      out.append("readObjectArray(").append(Util.READER_NAME).append(", null, new ").append(scope.requireFactoryGenerationAndGetName(typeHandler)).append(Util.TYPE_FACTORY_NAME_POSTFIX).append("()").append(", false)");
    }
    else {
      typeHandler.writeInstantiateCode(scope, out);
      out.append("(").append(Util.READER_NAME).append(')');
    }
    out.semi();

    out.closeBlock();
  }
}