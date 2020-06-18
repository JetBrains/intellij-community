// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.usages.impl.rules;

import com.intellij.BundleBase;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.usageView.UsageViewBundle;
import com.intellij.usages.UsageViewPresentation;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import java.util.function.Supplier;

public final class UsageType {
  public static final UsageType CLASS_INSTANCE_OF = new UsageType(UsageViewBundle.messagePointer("usage.type.instanceof"));
  public static final UsageType CLASS_IMPORT = new UsageType(UsageViewBundle.messagePointer("usage.type.import"));
  public static final UsageType CLASS_CAST_TO = new UsageType(UsageViewBundle.messagePointer("usage.type.cast.target"));
  public static final UsageType CLASS_EXTENDS_IMPLEMENTS_LIST = new UsageType(UsageViewBundle.messagePointer("usage.type.extends"));
  public static final UsageType CLASS_PERMITS_LIST = new UsageType(UsageViewBundle.messagePointer("usage.type.permits"));
  public static final UsageType CLASS_STATIC_MEMBER_ACCESS = new UsageType(UsageViewBundle.messagePointer("usage.type.static.member"));
  public static final UsageType CLASS_NESTED_CLASS_ACCESS = new UsageType(UsageViewBundle.messagePointer("usage.type.nested.class"));
  public static final UsageType CLASS_METHOD_THROWS_LIST = new UsageType(UsageViewBundle.messagePointer("usage.type.throws.list"));
  public static final UsageType CLASS_CLASS_OBJECT_ACCESS = new UsageType(UsageViewBundle.messagePointer("usage.type.class.object"));
  public static final UsageType CLASS_FIELD_DECLARATION = new UsageType(UsageViewBundle.messagePointer("usage.type.field.declaration"));
  public static final UsageType CLASS_LOCAL_VAR_DECLARATION = new UsageType(UsageViewBundle.messagePointer("usage.type.local.declaration"));
  public static final UsageType CLASS_METHOD_PARAMETER_DECLARATION = new UsageType(UsageViewBundle.messagePointer("usage.type.parameter.declaration"));
  public static final UsageType CLASS_CATCH_CLAUSE_PARAMETER_DECLARATION = new UsageType(UsageViewBundle.messagePointer("usage.type.catch.declaration"));
  public static final UsageType CLASS_METHOD_RETURN_TYPE = new UsageType(UsageViewBundle.messagePointer("usage.type.return"));
  public static final UsageType CLASS_NEW_OPERATOR = new UsageType(UsageViewBundle.messagePointer("usage.type.new"));
  public static final UsageType CLASS_ANONYMOUS_NEW_OPERATOR = new UsageType(UsageViewBundle.messagePointer("usage.type.new.anonymous"));
  public static final UsageType CLASS_NEW_ARRAY = new UsageType(UsageViewBundle.messagePointer("usage.type.new.array"));
  public static final UsageType ANNOTATION = new UsageType(UsageViewBundle.messagePointer("usage.type.annotation"));
  public static final UsageType TYPE_PARAMETER = new UsageType(UsageViewBundle.messagePointer("usage.type.type.parameter"));

  public static final UsageType READ = new UsageType(UsageViewBundle.messagePointer("usage.type.read"));
  public static final UsageType WRITE = new UsageType(UsageViewBundle.messagePointer("usage.type.write"));

  public static final UsageType LITERAL_USAGE = new UsageType(UsageViewBundle.messagePointer("usage.type.string.constant"));
  public static final UsageType COMMENT_USAGE = new UsageType(UsageViewBundle.messagePointer("usage.type.comment"));

  @SuppressWarnings("UnresolvedPropertyKey")
  public static final UsageType UNCLASSIFIED = new UsageType(UsageViewBundle.messagePointer("usage.type.unclassified"));

  public static final UsageType RECURSION = new UsageType(UsageViewBundle.messagePointer("usage.type.recursion"));
  public static final UsageType DELEGATE_TO_SUPER = new UsageType(UsageViewBundle.messagePointer("usage.type.delegate.to.super.method"));
  public static final UsageType DELEGATE_TO_SUPER_PARAMETERS_CHANGED = new UsageType(UsageViewBundle.messagePointer("usage.type.delegate.to.super.method.parameters.changed"));
  public static final UsageType DELEGATE_TO_ANOTHER_INSTANCE = new UsageType(UsageViewBundle.messagePointer("usage.type.delegate.to.another.instance.method"));
  public static final UsageType DELEGATE_TO_ANOTHER_INSTANCE_PARAMETERS_CHANGED = new UsageType(UsageViewBundle.messagePointer("usage.type.delegate.to.another.instance.method.parameters.changed"));

  private final Supplier<String> myNameComputable;

  /**
   * @deprecated Use {@link #UsageType(Supplier)} for I18n.
   */
  @Deprecated
  public UsageType(@NotNull @Nls(capitalization = Nls.Capitalization.Sentence) String name) {
    myNameComputable = () -> name;
  }

  public UsageType(@NotNull @Nls(capitalization = Nls.Capitalization.Sentence) Supplier<String> nameComputable) {
    myNameComputable = nameComputable;
  }

  @NotNull
  public String toString(@NotNull UsageViewPresentation presentation) {
    String word = presentation.getUsagesWord();
    String usageWord = StringUtil.startsWithChar(myNameComputable.get(), '{') ? StringUtil.capitalize(word) : word;
    return BundleBase.format(myNameComputable.get(), usageWord);
  }

  @Override
  public String toString() {
    return myNameComputable.get();
  }
}
