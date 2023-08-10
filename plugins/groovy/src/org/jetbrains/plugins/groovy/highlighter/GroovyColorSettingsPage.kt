// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.highlighter

import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.options.colors.AttributesDescriptor
import com.intellij.openapi.options.colors.ColorDescriptor
import com.intellij.openapi.options.colors.ColorSettingsPage
import com.intellij.util.containers.map2Array
import icons.JetgroovyIcons
import org.jetbrains.annotations.NonNls
import org.jetbrains.annotations.PropertyKey
import org.jetbrains.plugins.groovy.GroovyBundle
import org.jetbrains.plugins.groovy.highlighter.GroovySyntaxHighlighter.*
import javax.swing.Icon

class GroovyColorSettingsPage : ColorSettingsPage {

  private companion object {
    private operator fun Array<@PropertyKey(resourceBundle = "messages.GroovyBundle") String>
      .plus(attributeKey: TextAttributesKey): () -> AttributesDescriptor {
      return {
        @Suppress("HardCodedStringLiteral", "DialogTitleCapitalization")
        AttributesDescriptor(this@plus.joinToString("//", transform = { GroovyBundle.message(it) }), attributeKey)
      }
    }

    val attributes: Array<() -> AttributesDescriptor> = arrayOf(
      arrayOf("attribute.descriptor.annotations", "attribute.descriptor.annotation.attribute.name") + ANNOTATION_ATTRIBUTE_NAME,
      arrayOf("attribute.descriptor.annotations", "attribute.descriptor.annotation.name") + ANNOTATION,

      arrayOf("attribute.descriptor.braces.and.operators", "attribute.descriptor.braces") + BRACES,
      arrayOf("attribute.descriptor.braces.and.operators", "attribute.descriptor.closure.expression.braces.and.arrow") + CLOSURE_ARROW_AND_BRACES,
      arrayOf("attribute.descriptor.braces.and.operators", "attribute.descriptor.lambda.expression.braces.and.arrow") + LAMBDA_ARROW_AND_BRACES,
      arrayOf("attribute.descriptor.braces.and.operators", "attribute.descriptor.brackets") + BRACKETS,
      arrayOf("attribute.descriptor.braces.and.operators", "attribute.descriptor.parentheses") + PARENTHESES,
      arrayOf("attribute.descriptor.braces.and.operators", "attribute.descriptor.operator.sign") + OPERATION_SIGN,

      arrayOf("attribute.descriptor.comments", "attribute.descriptor.line.comment") + LINE_COMMENT,
      arrayOf("attribute.descriptor.comments", "attribute.descriptor.block.comment") + BLOCK_COMMENT,
      arrayOf("attribute.descriptor.comments", "attribute.descriptor.groovydoc", "attribute.descriptor.groovydoc.text") + DOC_COMMENT_CONTENT,
      arrayOf("attribute.descriptor.comments", "attribute.descriptor.groovydoc", "attribute.descriptor.groovydoc.tag") + DOC_COMMENT_TAG,

      arrayOf("attribute.descriptor.classes.and.interfaces", "attribute.descriptor.class") + CLASS_REFERENCE,
      arrayOf("attribute.descriptor.classes.and.interfaces", "attribute.descriptor.abstract.class") + ABSTRACT_CLASS_NAME,
      arrayOf("attribute.descriptor.classes.and.interfaces", "attribute.descriptor.anonymous.class") + ANONYMOUS_CLASS_NAME,
      arrayOf("attribute.descriptor.classes.and.interfaces", "attribute.descriptor.interface") + INTERFACE_NAME,
      arrayOf("attribute.descriptor.classes.and.interfaces", "attribute.descriptor.trait") + TRAIT_NAME,
      arrayOf("attribute.descriptor.classes.and.interfaces", "attribute.descriptor.enum") + ENUM_NAME,
      arrayOf("attribute.descriptor.classes.and.interfaces", "attribute.descriptor.type.parameter") + TYPE_PARAMETER,

      arrayOf("attribute.descriptor.methods", "attribute.descriptor.method.declaration") + METHOD_DECLARATION,
      arrayOf("attribute.descriptor.methods", "attribute.descriptor.constructor.declaration") + CONSTRUCTOR_DECLARATION,
      arrayOf("attribute.descriptor.methods", "attribute.descriptor.instance.method.call") + METHOD_CALL,
      arrayOf("attribute.descriptor.methods", "attribute.descriptor.static.method.call") + STATIC_METHOD_ACCESS,
      arrayOf("attribute.descriptor.methods", "attribute.descriptor.constructor.call") + CONSTRUCTOR_CALL,

      arrayOf("attribute.descriptor.fields", "attribute.descriptor.instance.field") + INSTANCE_FIELD,
      arrayOf("attribute.descriptor.fields", "attribute.descriptor.static.field") + STATIC_FIELD,

      arrayOf("attribute.descriptor.variables.and.parameters", "attribute.descriptor.local.variable") + LOCAL_VARIABLE,
      arrayOf("attribute.descriptor.variables.and.parameters", "attribute.descriptor.reassigned.local.variable") + REASSIGNED_LOCAL_VARIABLE,
      arrayOf("attribute.descriptor.variables.and.parameters", "attribute.descriptor.parameter") + PARAMETER,
      arrayOf("attribute.descriptor.variables.and.parameters", "attribute.descriptor.reassigned.parameter") + REASSIGNED_PARAMETER,

      arrayOf("attribute.descriptor.references", "attribute.descriptor.instance.property.reference") + INSTANCE_PROPERTY_REFERENCE,
      arrayOf("attribute.descriptor.references", "attribute.descriptor.static.property.reference") + STATIC_PROPERTY_REFERENCE,
      arrayOf("attribute.descriptor.references", "attribute.descriptor.unresolved.reference") + UNRESOLVED_ACCESS,

      arrayOf("attribute.descriptor.strings", "attribute.descriptor.string") + STRING,
      arrayOf("attribute.descriptor.strings", "attribute.descriptor.gstring") + GSTRING,
      arrayOf("attribute.descriptor.strings", "attribute.descriptor.valid.string.escape") + VALID_STRING_ESCAPE,
      arrayOf("attribute.descriptor.strings", "attribute.descriptor.invalid.string.escape") + INVALID_STRING_ESCAPE,

      arrayOf("attribute.descriptor.keyword") + KEYWORD,
      arrayOf("attribute.descriptor.number") + NUMBER,
      arrayOf("attribute.descriptor.bad.character") + BAD_CHARACTER,
      arrayOf("attribute.descriptor.list.map.to.object.conversion") + LITERAL_CONVERSION,
      arrayOf("attribute.descriptor.map.key.named.argument") + MAP_KEY,
      arrayOf("attribute.descriptor.label") + LABEL
      )

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

  override fun getDisplayName(): String = GroovyBundle.message("language.groovy")

  override fun getIcon(): Icon = JetgroovyIcons.Groovy.Groovy_16x16

  override fun getAttributeDescriptors(): Array<AttributesDescriptor> = attributes.map2Array { it() }

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
