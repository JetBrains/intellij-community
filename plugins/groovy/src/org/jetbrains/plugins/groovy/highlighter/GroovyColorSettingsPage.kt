// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.highlighter

import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.options.colors.AttributesDescriptor
import com.intellij.openapi.options.colors.ColorDescriptor
import com.intellij.openapi.options.colors.ColorSettingsPage
import icons.JetgroovyIcons
import org.jetbrains.annotations.NonNls
import org.jetbrains.plugins.groovy.highlighter.GroovySyntaxHighlighter.*
import javax.swing.Icon

class GroovyColorSettingsPage : ColorSettingsPage {

  private companion object {
    val attributes = mapOf(
      "Annotations//Annotation attribute name" to ANNOTATION_ATTRIBUTE_NAME,
      "Annotations//Annotation name" to ANNOTATION,

      "Braces and Operators//Braces" to BRACES,
      "Braces and Operators//Closure expression braces and arrow" to CLOSURE_ARROW_AND_BRACES,
      "Braces and Operators//Lambda expression braces and arrow " to LAMBDA_ARROW_AND_BRACES,
      "Braces and Operators//Brackets" to BRACKETS,
      "Braces and Operators//Parentheses" to PARENTHESES,
      "Braces and Operators//Operator sign" to OPERATION_SIGN,

      "Comments//Line comment" to LINE_COMMENT,
      "Comments//Block comment" to BLOCK_COMMENT,
      "Comments//Groovydoc//Text" to DOC_COMMENT_CONTENT,
      "Comments//Groovydoc//Tag" to DOC_COMMENT_TAG,

      "Classes and Interfaces//Class" to CLASS_REFERENCE,
      "Classes and Interfaces//Abstract class" to ABSTRACT_CLASS_NAME,
      "Classes and Interfaces//Anonymous class" to ANONYMOUS_CLASS_NAME,
      "Classes and Interfaces//Interface" to INTERFACE_NAME,
      "Classes and Interfaces//Trait" to TRAIT_NAME,
      "Classes and Interfaces//Enum" to ENUM_NAME,
      "Classes and Interfaces//Type parameter" to TYPE_PARAMETER,

      "Methods//Method declaration" to METHOD_DECLARATION,
      "Methods//Constructor declaration" to CONSTRUCTOR_DECLARATION,
      "Methods//Instance method call" to METHOD_CALL,
      "Methods//Static method call" to STATIC_METHOD_ACCESS,
      "Methods//Constructor call" to CONSTRUCTOR_CALL,

      "Fields//Instance field" to INSTANCE_FIELD,
      "Fields//Static field" to STATIC_FIELD,

      "Variables and Parameters//Local variable" to LOCAL_VARIABLE,
      "Variables and Parameters//Reassigned local variable" to REASSIGNED_LOCAL_VARIABLE,
      "Variables and Parameters//Parameter" to PARAMETER,
      "Variables and Parameters//Reassigned parameter" to REASSIGNED_PARAMETER,

      "References//Instance property reference" to INSTANCE_PROPERTY_REFERENCE,
      "References//Static property reference" to STATIC_PROPERTY_REFERENCE,
      "References//Unresolved reference" to UNRESOLVED_ACCESS,

      "Strings//String" to STRING,
      "Strings//GString" to GSTRING,
      "Strings//Valid string escape" to VALID_STRING_ESCAPE,
      "Strings//Invalid string escape" to INVALID_STRING_ESCAPE,

      "Keyword" to KEYWORD,
      "Number" to NUMBER,
      "Bad character" to BAD_CHARACTER,
      "List/map to object conversion" to LITERAL_CONVERSION,
      "Map key/Named argument" to MAP_KEY,
      "Label" to LABEL
    ).map { AttributesDescriptor(it.key, it.value) }.toTypedArray()

    val additionalTags = mapOf(
      "annotation" to ANNOTATION,
      "annotationAttribute" to ANNOTATION_ATTRIBUTE_NAME,

      "groovydoc" to DOC_COMMENT_CONTENT,
      "groovydocTag" to DOC_COMMENT_TAG,

      "class" to CLASS_REFERENCE,
      "abstractClass" to ABSTRACT_CLASS_NAME,
      "anonymousClass" to ANONYMOUS_CLASS_NAME,
      "interface" to INTERFACE_NAME,
      "trait" to TRAIT_NAME,
      "enum" to ENUM_NAME,
      "typeParameter" to TYPE_PARAMETER,

      "method" to METHOD_DECLARATION,
      "constructor" to CONSTRUCTOR_DECLARATION,
      "instanceMethodCall" to METHOD_CALL,
      "staticMethodCall" to STATIC_METHOD_ACCESS,
      "constructorCall" to CONSTRUCTOR_CALL,

      "instanceField" to INSTANCE_FIELD,
      "staticField" to STATIC_FIELD,

      "localVariable" to LOCAL_VARIABLE,
      "reassignedVariable" to REASSIGNED_LOCAL_VARIABLE,
      "parameter" to PARAMETER,
      "reassignedParameter" to REASSIGNED_PARAMETER,

      "instanceProperty" to INSTANCE_PROPERTY_REFERENCE,
      "staticProperty" to STATIC_PROPERTY_REFERENCE,
      "unresolved" to UNRESOLVED_ACCESS,

      "keyword" to KEYWORD,
      "literalConstructor" to LITERAL_CONVERSION,
      "mapKey" to MAP_KEY,
      "label" to LABEL,

      "validEscape" to VALID_STRING_ESCAPE,
      "invalidEscape" to INVALID_STRING_ESCAPE,

      "closureBraces" to CLOSURE_ARROW_AND_BRACES,
      "lambdaBraces" to LAMBDA_ARROW_AND_BRACES
    )
  }

  override fun getDisplayName(): String = "Groovy"

  override fun getIcon(): Icon? = JetgroovyIcons.Groovy.Groovy_16x16

  override fun getAttributeDescriptors(): Array<AttributesDescriptor> = attributes

  override fun getColorDescriptors(): Array<out ColorDescriptor> = ColorDescriptor.EMPTY_ARRAY

  override fun getHighlighter(): GroovySyntaxHighlighter = GroovySyntaxHighlighter()

  override fun getAdditionalHighlightingTagToDescriptorMap(): Map<String, TextAttributesKey> = additionalTags

  @NonNls
  override fun getDemoText(): String = """<keyword>package</keyword> highlighting
###

<groovydoc>/**
* This is Groovydoc comment
* <groovydocTag>@see</groovydocTag> java.lang.String#equals
*/</groovydoc>
<annotation>@Annotation</annotation>(<annotationAttribute>parameter</annotationAttribute> = 'value')
<keyword>class</keyword> <class>C</class> {

  <keyword>def</keyword> <instanceField>property</instanceField> = <keyword>new</keyword> <anonymousClass>I</anonymousClass>() {}
  <keyword>static</keyword> <keyword>def</keyword> <staticField>staticProperty</staticField> = []

  <constructor>C</constructor>() {}

  <keyword>def</keyword> <<typeParameter>T</typeParameter>> <typeParameter>T</typeParameter> <method>instanceMethod</method>(T <parameter>parameter</parameter>, <reassignedParameter>reassignedParameter</reassignedParameter>) {
    <reassignedParameter>reassignedParameter</reassignedParameter> = 1
    //This is a line comment
    <keyword>return</keyword> <parameter>parameter</parameter>
  }

  <keyword>def</keyword> <method>getStuff</method>() { 42 }
  <keyword>static</keyword> <keyword>boolean</keyword> <method>isStaticStuff</method>() { true }

  <keyword>static</keyword> <keyword>def</keyword> <method>staticMethod</method>(<keyword>int</keyword> <parameter>i</parameter>) {
    /* This is a block comment */
    <interface>Map</interface> <localVariable>map</localVariable> = [<mapKey>key1</mapKey>: 1, <mapKey>key2</mapKey>: 2, (22): 33]

    <keyword>def</keyword> <localVariable>cl</localVariable> = <closureBraces>{</closureBraces> <parameter>a</parameter> <closureBraces>-></closureBraces> <parameter>a</parameter> <closureBraces>}</closureBraces>
    <keyword>def</keyword> <localVariable>lambda</localVariable> = <parameter>b</parameter> <lambdaBraces>-></lambdaBraces> <lambdaBraces>{</lambdaBraces> <parameter>b</parameter> <lambdaBraces>}</lambdaBraces>

    <class>File</class> <localVariable>f</localVariable> = <literalConstructor>[</literalConstructor>'path'<literalConstructor>]</literalConstructor>
    <keyword>def</keyword> <reassignedVariable>a</reassignedVariable> = 'JetBrains'.<instanceMethodCall>matches</instanceMethodCall>(/Jw+Bw+/)

    <label>label</label>:
    <keyword>for</keyword> (<localVariable>entry</localVariable> <keyword>in</keyword> <localVariable>map</localVariable>) {
      <keyword>if</keyword> (<localVariable>entry</localVariable>.value > 1 && <parameter>i</parameter> < 2) {
        <reassignedVariable>a</reassignedVariable> = <unresolved>unresolvedReference</unresolved>
        <keyword>continue</keyword> label
      } <keyword>else</keyword> {
        <reassignedVariable>a</reassignedVariable> = <localVariable>entry</localVariable>
      }
    }

    <instanceMethodCall>print</instanceMethodCall> <localVariable>map</localVariable>.<mapKey>key1</mapKey>
  }
}

<keyword>def</keyword> <localVariable>c</localVariable> = <keyword>new</keyword> <constructorCall>C</constructorCall>()
<localVariable>c</localVariable>.<instanceMethodCall>instanceMethod</instanceMethodCall>("Hello<validEscape>\n</validEscape>", 'world<invalidEscape>\x</invalidEscape>')
<instanceMethodCall>println</instanceMethodCall> <localVariable>c</localVariable>.<instanceProperty>stuff</instanceProperty>

<class>C</class>.<staticMethodCall>staticMethod</staticMethodCall>(<mapKey>namedArg</mapKey>: 1)
<class>C</class>.<staticProperty>staticStuff</staticProperty>

<keyword>abstract</keyword> <keyword>class</keyword> <abstractClass>AbstractClass</abstractClass> {}
<keyword>interface</keyword> <interface>I</interface> {}
<keyword>trait</keyword> <trait>T</trait> {}
<keyword>enum</keyword> <enum>E</enum> {}
@<keyword>interface</keyword> <annotation>Annotation</annotation> {
  <class>String</class> <method>parameter</method>()
}
"""
}
