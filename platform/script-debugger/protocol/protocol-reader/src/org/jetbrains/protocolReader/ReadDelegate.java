package org.jetbrains.protocolReader;

import org.jetbrains.annotations.NotNull;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

class ReadDelegate {
  private static final List<String> STATIC_METHOD_PARAM_NAME_LIST = Collections.singletonList(Util.READER_NAME);
  private static final List<String> STATIC_METHOD_PARAM_NAME_LIST2 = Arrays.asList(Util.READER_NAME, "nextName");

  private final TypeHandler<?> typeHandler;
  private final boolean isList;

  private final List<String> paramNames;

  ReadDelegate(@NotNull TypeHandler<?> typeHandler, boolean isList, boolean hasNextNameParam) {
    this.typeHandler = typeHandler;
    this.isList = isList;

    paramNames = hasNextNameParam ? STATIC_METHOD_PARAM_NAME_LIST2 : STATIC_METHOD_PARAM_NAME_LIST;
  }

  void write(@NotNull ClassScope scope, @NotNull Method method, @NotNull TextOutput out) {
    MethodHandler.writeMethodDeclarationJava(out, method, paramNames);
    out.openBlock();
    out.append("return ");
    if (isList) {
      out.append("readObjectArray(").append(Util.READER_NAME).append(", null, new ").append(scope.requireFactoryGenerationAndGetName(typeHandler)).append(Util.TYPE_FACTORY_NAME_POSTFIX).append("()").append(", false)");
    }
    else {
      typeHandler.writeInstantiateCode(scope, out);
      out.append('(').append(Util.READER_NAME);
      out.comma().space();
      out.append(paramNames.size() == 1 ? "null" : "nextName");
      out.append(')');
    }
    out.semi();

    out.closeBlock();
  }
}