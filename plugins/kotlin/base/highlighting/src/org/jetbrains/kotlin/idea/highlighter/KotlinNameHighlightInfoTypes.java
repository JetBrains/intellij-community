// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.highlighter;

import com.intellij.codeInsight.daemon.impl.HighlightInfoType;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import org.jetbrains.annotations.NotNull;

/**
 * {@link HighlightInfoType}s used in Kotlin semantic highlighting
 */
public class KotlinNameHighlightInfoTypes {
    // default keys (mostly syntax elements)
    public static final @NotNull HighlightInfoType KEYWORD = createSymbolTypeInfo(KotlinHighlightingColors.KEYWORD);
    public static final HighlightInfoType BUILTIN_ANNOTATION = createSymbolTypeInfo(KotlinHighlightingColors.BUILTIN_ANNOTATION);
    public static final HighlightInfoType NUMBER = createSymbolTypeInfo(KotlinHighlightingColors.NUMBER);
    public static final HighlightInfoType STRING = createSymbolTypeInfo(KotlinHighlightingColors.STRING);
    public static final HighlightInfoType FUNCTION_LITERAL_BRACES_AND_ARROW = createSymbolTypeInfo(KotlinHighlightingColors.FUNCTION_LITERAL_BRACES_AND_ARROW);
    public static final HighlightInfoType COMMA = createSymbolTypeInfo(KotlinHighlightingColors.COMMA);
    public static final HighlightInfoType SEMICOLON = createSymbolTypeInfo(KotlinHighlightingColors.SEMICOLON);
    public static final HighlightInfoType COLON = createSymbolTypeInfo(KotlinHighlightingColors.COLON);
    public static final HighlightInfoType DOT = createSymbolTypeInfo(KotlinHighlightingColors.DOT);
    public static final HighlightInfoType SAFE_ACCESS = createSymbolTypeInfo(KotlinHighlightingColors.SAFE_ACCESS);
    public static final HighlightInfoType EXCLEXCL = createSymbolTypeInfo(KotlinHighlightingColors.EXCLEXCL);
    public static final HighlightInfoType ARROW = createSymbolTypeInfo(KotlinHighlightingColors.ARROW);
    public static final HighlightInfoType BLOCK_COMMENT = createSymbolTypeInfo(KotlinHighlightingColors.BLOCK_COMMENT);
    public static final HighlightInfoType KDOC_LINK = createSymbolTypeInfo(KotlinHighlightingColors.KDOC_LINK);
    // class kinds
    public static final HighlightInfoType CLASS = createSymbolTypeInfo(KotlinHighlightingColors.CLASS);
    public static final HighlightInfoType TYPE_PARAMETER = createSymbolTypeInfo(KotlinHighlightingColors.TYPE_PARAMETER);
    public static final HighlightInfoType ABSTRACT_CLASS = createSymbolTypeInfo(KotlinHighlightingColors.ABSTRACT_CLASS);
    public static final HighlightInfoType TRAIT = createSymbolTypeInfo(KotlinHighlightingColors.TRAIT);
    public static final HighlightInfoType ANNOTATION = createSymbolTypeInfo(KotlinHighlightingColors.ANNOTATION);
    public static final @NotNull HighlightInfoType OBJECT = createSymbolTypeInfo(KotlinHighlightingColors.OBJECT);
    public static final HighlightInfoType ENUM = createSymbolTypeInfo(KotlinHighlightingColors.ENUM);
    public static final HighlightInfoType ENUM_ENTRY = createSymbolTypeInfo(KotlinHighlightingColors.ENUM_ENTRY);
    public static final HighlightInfoType TYPE_ALIAS = createSymbolTypeInfo(KotlinHighlightingColors.TYPE_ALIAS);

    // variable kinds
    public static final HighlightInfoType MUTABLE_VARIABLE = createSymbolTypeInfo(KotlinHighlightingColors.MUTABLE_VARIABLE);
    public static final HighlightInfoType LOCAL_VARIABLE = createSymbolTypeInfo(KotlinHighlightingColors.LOCAL_VARIABLE);
    public static final HighlightInfoType PARAMETER = createSymbolTypeInfo(KotlinHighlightingColors.PARAMETER);
    public static final HighlightInfoType WRAPPED_INTO_REF = createSymbolTypeInfo(KotlinHighlightingColors.WRAPPED_INTO_REF);
    public static final HighlightInfoType INSTANCE_PROPERTY = createSymbolTypeInfo(KotlinHighlightingColors.INSTANCE_PROPERTY);
    public static final HighlightInfoType PACKAGE_PROPERTY = createSymbolTypeInfo(KotlinHighlightingColors.PACKAGE_PROPERTY);
    public static final HighlightInfoType BACKING_FIELD_VARIABLE = createSymbolTypeInfo(KotlinHighlightingColors.BACKING_FIELD_VARIABLE);
    public static final HighlightInfoType EXTENSION_PROPERTY = createSymbolTypeInfo(KotlinHighlightingColors.EXTENSION_PROPERTY);
    public static final HighlightInfoType SYNTHETIC_EXTENSION_PROPERTY = createSymbolTypeInfo(KotlinHighlightingColors.SYNTHETIC_EXTENSION_PROPERTY);
    public static final HighlightInfoType DYNAMIC_PROPERTY_CALL = createSymbolTypeInfo(KotlinHighlightingColors.DYNAMIC_PROPERTY_CALL);
    public static final HighlightInfoType ANDROID_EXTENSIONS_PROPERTY_CALL = createSymbolTypeInfo(KotlinHighlightingColors.ANDROID_EXTENSIONS_PROPERTY_CALL);
    public static final HighlightInfoType INSTANCE_PROPERTY_CUSTOM_PROPERTY_DECLARATION = createSymbolTypeInfo(KotlinHighlightingColors.INSTANCE_PROPERTY_CUSTOM_PROPERTY_DECLARATION);
    public static final HighlightInfoType PACKAGE_PROPERTY_CUSTOM_PROPERTY_DECLARATION = createSymbolTypeInfo(KotlinHighlightingColors.PACKAGE_PROPERTY_CUSTOM_PROPERTY_DECLARATION);
    // functions
    public static final HighlightInfoType FUNCTION_LITERAL_DEFAULT_PARAMETER = createSymbolTypeInfo(KotlinHighlightingColors.FUNCTION_LITERAL_DEFAULT_PARAMETER);
    public static final HighlightInfoType FUNCTION_DECLARATION = createSymbolTypeInfo(KotlinHighlightingColors.FUNCTION_DECLARATION);
    public static final @NotNull HighlightInfoType FUNCTION_CALL = createSymbolTypeInfo(KotlinHighlightingColors.FUNCTION_CALL);
    public static final @NotNull HighlightInfoType PACKAGE_FUNCTION_CALL = createSymbolTypeInfo(KotlinHighlightingColors.PACKAGE_FUNCTION_CALL);
    public static final @NotNull HighlightInfoType EXTENSION_FUNCTION_CALL = createSymbolTypeInfo(KotlinHighlightingColors.EXTENSION_FUNCTION_CALL);
    public static final @NotNull HighlightInfoType CONSTRUCTOR_CALL = createSymbolTypeInfo(KotlinHighlightingColors.CONSTRUCTOR_CALL);
    public static final @NotNull HighlightInfoType DYNAMIC_FUNCTION_CALL = createSymbolTypeInfo(KotlinHighlightingColors.DYNAMIC_FUNCTION_CALL);
    public static final @NotNull HighlightInfoType SUSPEND_FUNCTION_CALL = createSymbolTypeInfo(KotlinHighlightingColors.SUSPEND_FUNCTION_CALL);
    public static final @NotNull HighlightInfoType VARIABLE_AS_FUNCTION_CALL = createSymbolTypeInfo(KotlinHighlightingColors.VARIABLE_AS_FUNCTION_CALL);
    public static final @NotNull HighlightInfoType VARIABLE_AS_FUNCTION_LIKE_CALL = createSymbolTypeInfo(KotlinHighlightingColors.VARIABLE_AS_FUNCTION_LIKE_CALL);

    // other
    public static final HighlightInfoType SMART_CAST_VALUE = createSymbolTypeInfo(KotlinHighlightingColors.SMART_CAST_VALUE);
    public static final HighlightInfoType SMART_CONSTANT = createSymbolTypeInfo(KotlinHighlightingColors.SMART_CONSTANT);
    public static final HighlightInfoType SMART_CAST_RECEIVER = createSymbolTypeInfo(KotlinHighlightingColors.SMART_CAST_RECEIVER);
    public static final HighlightInfoType LABEL = createSymbolTypeInfo(KotlinHighlightingColors.LABEL);
    public static final HighlightInfoType NAMED_ARGUMENT = createSymbolTypeInfo(KotlinHighlightingColors.NAMED_ARGUMENT);
    public static final HighlightInfoType ANNOTATION_ATTRIBUTE_NAME_ATTRIBUTES = createSymbolTypeInfo(KotlinHighlightingColors.ANNOTATION_ATTRIBUTE_NAME_ATTRIBUTES);

    @NotNull
    private static HighlightInfoType createSymbolTypeInfo(@NotNull TextAttributesKey attributesKey) {
      return new HighlightInfoType.HighlightInfoTypeImpl(HighlightInfoType.SYMBOL_TYPE_SEVERITY, attributesKey, false);
    }
}
