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

package org.jetbrains.plugins.groovy.lang.parser;

import com.intellij.lang.ASTNode;
import com.intellij.psi.stubs.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;
import com.intellij.util.io.StringRef;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.GroovyFileType;
import org.jetbrains.plugins.groovy.lang.groovydoc.parser.GroovyDocElementTypes;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyElementType;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.GrModifierList;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.annotation.GrAnnotation;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrField;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariableDeclaration;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameter;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameterList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.*;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrEnumConstant;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrTypeParameter;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrTypeParameterList;
import org.jetbrains.plugins.groovy.lang.psi.impl.auxiliary.annotation.GrAnnotationImpl;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.GrVariableDeclarationBase;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.blocks.GrBlockImpl;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.blocks.GrClosableBlockImpl;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.blocks.GrOpenBlockImpl;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.params.GrParameterImpl;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.params.GrParameterListImpl;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.typedef.*;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.typedef.members.GrAnnotationMethodImpl;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.typedef.members.GrConstructorImpl;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.typedef.members.GrMethodImpl;
import org.jetbrains.plugins.groovy.lang.psi.impl.types.GrTypeParameterImpl;
import org.jetbrains.plugins.groovy.lang.psi.impl.types.GrTypeParameterListImpl;
import org.jetbrains.plugins.groovy.lang.psi.stubs.*;
import org.jetbrains.plugins.groovy.lang.psi.stubs.elements.*;
import org.jetbrains.plugins.groovy.lang.psi.stubs.index.GrAnnotationMethodNameIndex;

import java.io.IOException;

/**
 * Utility interface that contains all Groovy non-token element types
 *
 * @author Dmitry.Krasilschikov, ilyas
 */
public interface GroovyElementTypes extends GroovyTokenTypes, GroovyDocElementTypes {

  /*
  Stub elements
   */
  GrStubElementType<GrTypeDefinitionStub, GrClassDefinition> CLASS_DEFINITION =
    new GrTypeDefinitionElementType<GrClassDefinition>("class definition") {
      public GrClassDefinition createPsi(GrTypeDefinitionStub stub) {
        return new GrClassDefinitionImpl(stub);
      }
    };
  GrStubElementType<GrTypeDefinitionStub, GrInterfaceDefinition> INTERFACE_DEFINITION =
    new GrTypeDefinitionElementType<GrInterfaceDefinition>("interface definition") {
      public GrInterfaceDefinition createPsi(GrTypeDefinitionStub stub) {
        return new GrInterfaceDefinitionImpl(stub);
      }
    };
  GrStubElementType<GrTypeDefinitionStub, GrEnumTypeDefinition> ENUM_DEFINITION =
    new GrTypeDefinitionElementType<GrEnumTypeDefinition>("enumeration definition") {
      public GrEnumTypeDefinition createPsi(GrTypeDefinitionStub stub) {
        return new GrEnumTypeDefinitionImpl(stub);
      }
    };
  GrStubElementType<GrTypeDefinitionStub, GrAnnotationTypeDefinition> ANNOTATION_DEFINITION =
    new GrTypeDefinitionElementType<GrAnnotationTypeDefinition>("annotation definition") {
      public GrAnnotationTypeDefinition createPsi(GrTypeDefinitionStub stub) {
        return new GrAnnotationTypeDefinitionImpl(stub);
      }
    };
  GrStubElementType<GrTypeDefinitionStub, GrAnonymousClassDefinition> ANONYMOUS_CLASS_DEFINITION =
    new GrTypeDefinitionElementType<GrAnonymousClassDefinition>("Anonymous class") {
      @Override
      public GrAnonymousClassDefinition createPsi(GrTypeDefinitionStub stub) {
        return new GrAnonymousClassDefinitionImpl(stub);
      }
    };

  TokenSet TYPE_DEFINITION_TYPES = TokenSet.create(CLASS_DEFINITION, INTERFACE_DEFINITION, ENUM_DEFINITION, ANNOTATION_DEFINITION);

  GrStubElementType<GrFieldStub, GrEnumConstant> ENUM_CONSTANT = new GrEnumConstantElementType();
  GrStubElementType<GrFieldStub, GrField> FIELD = new GrFieldElementType();
  GrMethodElementType METHOD_DEFINITION = new GrMethodElementType("method definition") {

    public GrMethod createPsi(GrMethodStub stub) {
      return new GrMethodImpl(stub);
    }
  };
  GrStubElementType<GrMethodStub, GrMethod> ANNOTATION_METHOD = new GrMethodElementType("annotation method") {
    @Override
    public GrMethod createPsi(GrMethodStub stub) {
      return new GrAnnotationMethodImpl(stub);
    }

    @Override
    public void indexStub(GrMethodStub stub, IndexSink sink) {
      super.indexStub(stub, sink);
      String name = stub.getName();
      if (name != null) {
        sink.occurrence(GrAnnotationMethodNameIndex.KEY, name);
      }
    }
  };

  GrReferenceListElementType<GrImplementsClause> IMPLEMENTS_CLAUSE = new GrReferenceListElementType<GrImplementsClause>("implements clause") {
    public GrImplementsClause createPsi(GrReferenceListStub stub) {
      return new GrImplementsClauseImpl(stub);
    }
  };
  GrReferenceListElementType<GrExtendsClause> EXTENDS_CLAUSE = new GrReferenceListElementType<GrExtendsClause>("super class clause") {
    public GrExtendsClause createPsi(GrReferenceListStub stub) {
      return new GrExtendsClauseImpl(stub);
    }
  };


  GroovyElementType NONE = new GroovyElementType("no token"); //not a node

  // Indicates the wrongway of parsing
  GroovyElementType WRONGWAY = new GroovyElementType("Wrong way!");
  GroovyElementType LITERAL = new GroovyElementType("Literal");
  //Packaging
  GroovyElementType PACKAGE_DEFINITION = new GroovyElementType("Package definition");

  GrCodeBlockElementType CLOSABLE_BLOCK = new GrCodeBlockElementType("Closable block") {
    @NotNull
    @Override
    public GrBlockImpl createNode(CharSequence text) {
      return new GrClosableBlockImpl(this, text);
    }
  };
  GrCodeBlockElementType OPEN_BLOCK = new GrCodeBlockElementType("Open block") {
    @NotNull
    @Override
    public GrBlockImpl createNode(CharSequence text) {
      return new GrOpenBlockImpl(this, text);
    }
  };
  GrCodeBlockElementType CONSTRUCTOR_BODY = new GrCodeBlockElementType("Constructor body") {
    @NotNull
    @Override
    public GrBlockImpl createNode(CharSequence text) {
      return new GrOpenBlockImpl(this, text);
    }
  };

  GroovyElementType BLOCK_STATEMENT = new GroovyElementType("Block statement");

  // Enum
  GroovyElementType ENUM_CONSTANTS = new GroovyElementType("Enumeration constants");
  GroovyElementType IMPORT_STATEMENT = new GroovyElementType("Import statement");
  //Branch statements
  GroovyElementType BREAK_STATEMENT = new GroovyElementType("Break statement");
  GroovyElementType CONTINUE_STATEMENT = new GroovyElementType("Continue statement");

  GroovyElementType RETURN_STATEMENT = new GroovyElementType("Return statement");
  GroovyElementType ASSERT_STATEMENT = new GroovyElementType("Assert statement");
  GroovyElementType THROW_STATEMENT = new GroovyElementType("Throw statement");
  // Expression statements
  GroovyElementType LABELED_STATEMENT = new GroovyElementType("Labeled statement");
  GroovyElementType CALL_EXPRESSION = new GroovyElementType("Expression statement");
  GroovyElementType COMMAND_ARGUMENTS = new GroovyElementType("Command argument");
  GroovyElementType CONDITIONAL_EXPRESSION = new GroovyElementType("Conditional expression");
  GroovyElementType ELVIS_EXPRESSION = new GroovyElementType("Elvis expression");
  GroovyElementType ASSIGNMENT_EXPRESSION = new GroovyElementType("Assignment expression");
  GroovyElementType LOGICAL_OR_EXPRESSION = new GroovyElementType("Logical OR expression");
  GroovyElementType LOGICAL_AND_EXPRESSION = new GroovyElementType("Logical AND expression");
  GroovyElementType INCLUSIVE_OR_EXPRESSION = new GroovyElementType("Inclusive OR expression");
  GroovyElementType EXCLUSIVE_OR_EXPRESSION = new GroovyElementType("Exclusive OR expression");
  GroovyElementType AND_EXPRESSION = new GroovyElementType("AND expression");
  GroovyElementType REGEX_FIND_EXPRESSION = new GroovyElementType("Regex Find expression");
  GroovyElementType REGEX_MATCH_EXPRESSION = new GroovyElementType("Regex Match expression");
  GroovyElementType EQUALITY_EXPRESSION = new GroovyElementType("Equality expression");
  GroovyElementType RELATIONAL_EXPRESSION = new GroovyElementType("Relational expression");
  GroovyElementType SHIFT_EXPRESSION = new GroovyElementType("Shift expression");
  GroovyElementType RANGE_EXPRESSION = new GroovyElementType("Range expression");
  GroovyElementType COMPOSITE_LSHIFT_SIGN = new GroovyElementType("Composite shift sign <<");
  GroovyElementType COMPOSITE_RSHIFT_SIGN = new GroovyElementType("Composite shift sign >>");
  GroovyElementType COMPOSITE_TRIPLE_SHIFT_SIGN = new GroovyElementType("Composite shift sign >>>");
  GroovyElementType MORE_OR_EQUALS_SIGN = new GroovyElementType(">=");
  GroovyElementType ADDITIVE_EXPRESSION = new GroovyElementType("Additive expression");
  GroovyElementType MULTIPLICATIVE_EXPRESSION = new GroovyElementType("Multiplicative expression");
  GroovyElementType POWER_EXPRESSION = new GroovyElementType("Power expression");
  GroovyElementType POWER_EXPRESSION_SIMPLE = new GroovyElementType("Simple power expression");
  GroovyElementType UNARY_EXPRESSION = new GroovyElementType("Unary expression");
  GroovyElementType CAST_EXPRESSION = new GroovyElementType("cast expression");
  GroovyElementType SAFE_CAST_EXPRESSION = new GroovyElementType("safe cast expression");
  GroovyElementType INSTANCEOF_EXPRESSION = new GroovyElementType("instanceof expression");
  GroovyElementType POSTFIX_EXPRESSION = new GroovyElementType("Postfix expression");
  GroovyElementType PATH_PROPERTY_REFERENCE = new GroovyElementType("Property reference");

  GroovyElementType PATH_METHOD_CALL = new GroovyElementType("Method call");

  GroovyElementType PATH_INDEX_PROPERTY = new GroovyElementType("Index property");
  GroovyElementType PARENTHESIZED_EXPRESSION = new GroovyElementType("Parenthesized expression");
  // Plain label
  GroovyElementType LABEL = new GroovyElementType("Label");

  // Arguments
  GroovyElementType ARGUMENTS = new GroovyElementType("Arguments");
  GroovyElementType ARGUMENT = new GroovyElementType("Compound argument");
  GroovyElementType ARGUMENT_LABEL = new GroovyElementType("Argument label");
  // Simple expression
  GroovyElementType PATH_PROPERTY = new GroovyElementType("Path name selector");
  GroovyElementType REFERENCE_EXPRESSION = new GroovyElementType("Reference expressions");
  GroovyElementType THIS_REFERENCE_EXPRESSION = new GroovyElementType("This reference expressions");
  GroovyElementType SUPER_REFERENCE_EXPRESSION = new GroovyElementType("Super reference expressions");

  GroovyElementType NEW_EXPRESSION = new GroovyElementType("New expressions");

  GroovyElementType BUILT_IN_TYPE_EXPRESSION = new GroovyElementType("Built in type expression");

  // Lists & maps
  GroovyElementType LIST_OR_MAP = new GroovyElementType("Generalized list");
  // Type Elements
  GroovyElementType ARRAY_TYPE = new GroovyElementType("Array type");

  GroovyElementType BUILT_IN_TYPE = new GroovyElementType("Built in type");

  // GStrings
  GroovyElementType GSTRING = new GroovyElementType("GString");
  IElementType GSTRING_INJECTION =new GroovyElementType("Gstring injection");

  GroovyElementType REGEX = new GroovyElementType("Regular expression");
  //types
  GroovyElementType REFERENCE_ELEMENT = new GroovyElementType("reference element");
  GroovyElementType ARRAY_DECLARATOR = new GroovyElementType("array declarator");

  GroovyElementType TYPE_ARGUMENTS = new GroovyElementType("type arguments");
  GroovyElementType TYPE_ARGUMENT = new GroovyElementType("type argument");
  EmptyStubElementType<GrTypeParameterList> TYPE_PARAMETER_LIST = new EmptyStubElementType<GrTypeParameterList>("type parameter list", GroovyFileType.GROOVY_LANGUAGE) {
    @Override
    public GrTypeParameterList createPsi(EmptyStub stub) {
      return new GrTypeParameterListImpl(stub);
    }
  };

  GrStubElementType<GrTypeParameterStub, GrTypeParameter> TYPE_PARAMETER = new GrStubElementType<GrTypeParameterStub, GrTypeParameter>("type parameter") {
    @Override
    public GrTypeParameter createPsi(GrTypeParameterStub stub) {
      return new GrTypeParameterImpl(stub);
    }

    @Override
    public GrTypeParameterStub createStub(GrTypeParameter psi, StubElement parentStub) {
      return new GrTypeParameterStub(parentStub, StringRef.fromString(psi.getName()));
    }

    @Override
    public void serialize(GrTypeParameterStub stub, StubOutputStream dataStream) throws IOException {
      dataStream.writeName(stub.getName());
    }

    @Override
    public GrTypeParameterStub deserialize(StubInputStream dataStream, StubElement parentStub) throws IOException {
      return new GrTypeParameterStub(parentStub, dataStream.readName());
    }
  };
  GroovyElementType TYPE_PARAMETER_EXTENDS_BOUND_LIST = new GroovyElementType("type extends list");

  GroovyElementType DEFAULT_ANNOTATION_VALUE = new GroovyElementType("default annotation value");

  GrMethodElementType CONSTRUCTOR_DEFINITION = new GrMethodElementType("constructor definition") {
    @Override
    public GrMethod createPsi(GrMethodStub stub) {
      return new GrConstructorImpl(stub);
    }
  };

  GroovyElementType EXPLICIT_CONSTRUCTOR = new GroovyElementType("explicit constructor invokation");

  //throws
  GroovyElementType THROW_CLAUSE = new GroovyElementType("throw clause");
  //annotation
  GroovyElementType ANNOTATION_ARRAY_INITIALIZER = new GroovyElementType("annotation array initializer");
  GroovyElementType ANNOTATION_ARGUMENTS = new GroovyElementType("annotation arguments");
  GroovyElementType ANNOTATION_MEMBER_VALUE_PAIR = new GroovyElementType("annotation member value pair");
  GroovyElementType ANNOTATION_MEMBER_VALUE_PAIRS = new GroovyElementType("annotation member value pairs");

  GrStubElementType<GrAnnotationStub, GrAnnotation> ANNOTATION = new GrStubElementType<GrAnnotationStub, GrAnnotation>("annotation") {

    @Override
    public GrAnnotation createPsi(GrAnnotationStub stub) {
      return new GrAnnotationImpl(stub);
    }

    @Override
    public GrAnnotationStub createStub(GrAnnotation psi, StubElement parentStub) {
      return new GrAnnotationStub(parentStub, psi);
    }

    @Override
    public void serialize(GrAnnotationStub stub, StubOutputStream dataStream) throws IOException {
      dataStream.writeName(stub.getAnnotationName());
    }

    @Override
    public GrAnnotationStub deserialize(StubInputStream dataStream, StubElement parentStub) throws IOException {
      return new GrAnnotationStub(parentStub, dataStream.readName());
    }
  };
  //parameters
  EmptyStubElementType<GrParameterList> PARAMETERS_LIST = new EmptyStubElementType<GrParameterList>("parameters list", GroovyFileType.GROOVY_LANGUAGE) {
    @Override
    public GrParameterList createPsi(EmptyStub stub) {
      return new GrParameterListImpl(stub);
    }
  };

  GrStubElementType<GrParameterStub, GrParameter> PARAMETER = new GrStubElementType<GrParameterStub, GrParameter>("parameter") {
    @Override
    public GrParameter createPsi(GrParameterStub stub) {
      return new GrParameterImpl(stub);
    }

    @Override
    public GrParameterStub createStub(GrParameter psi, StubElement parentStub) {
      return new GrParameterStub(parentStub, StringRef.fromString(psi.getName()), GrStubUtils.getAnnotationNames(psi), GrStubUtils.getTypeText(psi));
    }

    @Override
    public void serialize(GrParameterStub stub, StubOutputStream dataStream) throws IOException {
      dataStream.writeName(stub.getName());
      GrStubUtils.writeStringArray(dataStream, stub.getAnnotations());
      GrStubUtils.writeNullableString(dataStream, stub.getTypeText());
    }

    @Override
    public GrParameterStub deserialize(StubInputStream dataStream, StubElement parentStub) throws IOException {
      final StringRef name = dataStream.readName();
      final String[] annotations = GrStubUtils.readStringArray(dataStream);
      final String typeText = GrStubUtils.readNullableString(dataStream);
      return new GrParameterStub(parentStub, name, annotations, typeText);
    }
  };
  EmptyStubElementType<GrTypeDefinitionBody> CLASS_BODY = new EmptyStubElementType<GrTypeDefinitionBody>("class block", GroovyFileType.GROOVY_LANGUAGE) {
      @Override
      public GrTypeDefinitionBody createPsi(EmptyStub stub) {
        return new GrTypeDefinitionBodyBase.GrClassBody(stub);
      }
    };

  IElementType ENUM_BODY = new GroovyElementType("enum block");
  //statements
  GroovyElementType IF_STATEMENT = new GroovyElementType("if statement");
  GroovyElementType FOR_STATEMENT = new GroovyElementType("for statement");

  GroovyElementType WHILE_STATEMENT = new GroovyElementType("while statement");
  // switch dtatement
  GroovyElementType SWITCH_STATEMENT = new GroovyElementType("switch statement");
  GroovyElementType CASE_SECTION = new GroovyElementType("case block");

  GroovyElementType CASE_LABEL = new GroovyElementType("case label");
  //for clauses
  GroovyElementType FOR_IN_CLAUSE = new GroovyElementType("IN clause");

  GroovyElementType FOR_TRADITIONAL_CLAUSE = new GroovyElementType("Traditional clause");
  GroovyElementType TRY_BLOCK_STATEMENT = new GroovyElementType("try block statement");
  GroovyElementType CATCH_CLAUSE = new GroovyElementType("catch clause");
  GroovyElementType FINALLY_CLAUSE = new GroovyElementType("finally clause");
  GroovyElementType SYNCHRONIZED_STATEMENT = new GroovyElementType("synchronized block statement");
  GroovyElementType CLASS_INITIALIZER = new GroovyElementType("static compound statement");

  EmptyStubElementType<GrVariableDeclaration> VARIABLE_DEFINITION_ERROR = new EmptyStubElementType<GrVariableDeclaration>("variable definitions with errors", GroovyFileType.GROOVY_LANGUAGE) {
    @Override
    public boolean shouldCreateStub(ASTNode node) {
      return false;
    }

    @Override
    public GrVariableDeclaration createPsi(EmptyStub stub) {
      throw new UnsupportedOperationException("Not implemented");
    }
  };
  EmptyStubElementType<GrVariableDeclaration> VARIABLE_DEFINITION =
    new EmptyStubElementType<GrVariableDeclaration>("variable definitions", GroovyFileType.GROOVY_LANGUAGE) {
      @Override
      public GrVariableDeclaration createPsi(EmptyStub stub) {
        return new GrVariableDeclarationBase.GrVariables(stub);
      }
    };
  IElementType MULTIPLE_VARIABLE_DEFINITION = new GroovyElementType("multivariable definition");
  GroovyElementType TUPLE_DECLARATION = new GroovyElementType("tuple declaration");
  GroovyElementType TUPLE_EXPRESSION = new GroovyElementType("tuple expression");


  GroovyElementType TUPLE_ERROR = new GroovyElementType("tuple with error");

  GroovyElementType VARIABLE = new GroovyElementType("assigned variable");

  //modifiers
  GrStubElementType<GrModifierListStub, GrModifierList> MODIFIERS = new GrModifierListElementType("modifier list");

  GroovyElementType BALANCED_BRACKETS = new GroovyElementType("balanced brackets"); //node

  //types
  GroovyElementType CLASS_TYPE_ELEMENT = new GroovyElementType("class type element"); //node

  TokenSet BLOCK_SET = TokenSet.create(CLOSABLE_BLOCK,
          BLOCK_STATEMENT,
          CONSTRUCTOR_BODY,
          OPEN_BLOCK,
          ENUM_BODY,
          CLASS_BODY);

  TokenSet METHOD_DEFS = TokenSet.create(METHOD_DEFINITION, CONSTRUCTOR_DEFINITION, ANNOTATION_METHOD);
  TokenSet VARIABLES = TokenSet.create(VARIABLE, FIELD);
  TokenSet TYPE_ELEMENTS = TokenSet.create(CLASS_TYPE_ELEMENT, ARRAY_TYPE, BUILT_IN_TYPE, TYPE_ARGUMENT);

}
