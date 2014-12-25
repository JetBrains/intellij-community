/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.usages.impl.rules;

import com.intellij.BundleBase;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.usageView.UsageViewBundle;
import com.intellij.usages.UsageViewPresentation;
import org.jetbrains.annotations.NotNull;

public final class UsageType {
  public static final UsageType CLASS_INSTANCE_OF = new UsageType(UsageViewBundle.message("usage.type.instanceof"));
  public static final UsageType CLASS_IMPORT = new UsageType(UsageViewBundle.message("usage.type.import"));
  public static final UsageType CLASS_CAST_TO = new UsageType(UsageViewBundle.message("usage.type.cast.target"));
  public static final UsageType CLASS_EXTENDS_IMPLEMENTS_LIST = new UsageType(UsageViewBundle.message("usage.type.extends"));
  public static final UsageType CLASS_STATIC_MEMBER_ACCESS = new UsageType(UsageViewBundle.message("usage.type.static.member"));
  public static final UsageType CLASS_NESTED_CLASS_ACCESS = new UsageType(UsageViewBundle.message("usage.type.nested.class"));
  public static final UsageType CLASS_METHOD_THROWS_LIST = new UsageType(UsageViewBundle.message("usage.type.throws.list"));
  public static final UsageType CLASS_CLASS_OBJECT_ACCESS = new UsageType(UsageViewBundle.message("usage.type.class.object"));
  public static final UsageType CLASS_FIELD_DECLARATION = new UsageType(UsageViewBundle.message("usage.type.field.declaration"));
  public static final UsageType CLASS_LOCAL_VAR_DECLARATION = new UsageType(UsageViewBundle.message("usage.type.local.declaration"));
  public static final UsageType CLASS_METHOD_PARAMETER_DECLARATION = new UsageType(UsageViewBundle.message("usage.type.parameter.declaration"));
  public static final UsageType CLASS_CATCH_CLAUSE_PARAMETER_DECLARATION = new UsageType(UsageViewBundle.message("usage.type.catch.declaration"));
  public static final UsageType CLASS_METHOD_RETURN_TYPE = new UsageType(UsageViewBundle.message("usage.type.return"));
  public static final UsageType CLASS_NEW_OPERATOR = new UsageType(UsageViewBundle.message("usage.type.new"));
  public static final UsageType CLASS_ANONYMOUS_NEW_OPERATOR = new UsageType(UsageViewBundle.message("usage.type.new.anonymous"));
  public static final UsageType CLASS_NEW_ARRAY = new UsageType(UsageViewBundle.message("usage.type.new.array"));
  public static final UsageType ANNOTATION = new UsageType(UsageViewBundle.message("usage.type.annotation"));
  public static final UsageType TYPE_PARAMETER = new UsageType(UsageViewBundle.message("usage.type.type.parameter"));

  public static final UsageType READ = new UsageType(UsageViewBundle.message("usage.type.read"));
  public static final UsageType WRITE = new UsageType(UsageViewBundle.message("usage.type.write"));

  public static final UsageType LITERAL_USAGE = new UsageType(UsageViewBundle.message("usage.type.string.constant"));
  public static final UsageType COMMENT_USAGE = new UsageType(UsageViewBundle.message("usage.type.comment"));

  @SuppressWarnings("UnresolvedPropertyKey")
  public static final UsageType UNCLASSIFIED = new UsageType(UsageViewBundle.message("usage.type.unclassified"));

  public static final UsageType RECURSION = new UsageType("Recursion");
  public static final UsageType DELEGATE_TO_SUPER = new UsageType("Delegate to super method");
  public static final UsageType DELEGATE_TO_SUPER_PARAMETERS_CHANGED = new UsageType("Delegate to super method, parameters changed");
  public static final UsageType DELEGATE_TO_ANOTHER_INSTANCE = new UsageType("Delegate to another instance method");
  public static final UsageType DELEGATE_TO_ANOTHER_INSTANCE_PARAMETERS_CHANGED = new UsageType("Delegate to another instance method, parameters changed");

  private final String myName;

  public UsageType(@NotNull String name) {
    myName = name;
  }

  @NotNull
  public String toString(@NotNull UsageViewPresentation presentation) {
    String word = presentation.getUsagesWord();
    String usageWord = StringUtil.startsWithChar(myName, '{') ? StringUtil.capitalize(word) : word;
    return BundleBase.format(myName, usageWord);
  }

  @Override
  public String toString() {
    return myName;
  }
}
