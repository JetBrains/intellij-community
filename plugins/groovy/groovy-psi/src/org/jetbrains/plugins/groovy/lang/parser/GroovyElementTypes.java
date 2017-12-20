// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.plugins.groovy.lang.parser;

import com.intellij.lang.ASTNode;
import com.intellij.psi.stubs.*;
import com.intellij.psi.tree.IStubFileElementType;
import com.intellij.util.io.StringRef;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.GroovyLanguage;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyElementType;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.GrThrowsClause;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.GrModifierList;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.annotation.GrAnnotation;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrField;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariableDeclaration;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameter;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameterList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.*;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrEnumConstant;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrEnumConstantList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrTypeParameter;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrTypeParameterList;
import org.jetbrains.plugins.groovy.lang.psi.impl.auxiliary.GrThrowsClauseImpl;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.blocks.GrBlockImpl;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.blocks.GrClosableBlockImpl;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.blocks.GrOpenBlockImpl;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.params.GrParameterImpl;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.params.GrParameterListImpl;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.typedef.*;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.typedef.enumConstant.GrEnumConstantListImpl;
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
public interface GroovyElementTypes {

  /*
  Stub elements
   */
  GrStubElementType<GrTypeDefinitionStub, GrClassDefinition> CLASS_DEFINITION =
    new GrTypeDefinitionElementType<GrClassDefinition>("class definition") {
      @Override
      public GrClassDefinition createPsi(@NotNull GrTypeDefinitionStub stub) {
        return new GrClassDefinitionImpl(stub);
      }
    };
  GrStubElementType<GrTypeDefinitionStub, GrInterfaceDefinition> INTERFACE_DEFINITION =
    new GrTypeDefinitionElementType<GrInterfaceDefinition>("interface definition") {
      @Override
      public GrInterfaceDefinition createPsi(@NotNull GrTypeDefinitionStub stub) {
        return new GrInterfaceDefinitionImpl(stub);
      }
    };
  GrStubElementType<GrTypeDefinitionStub, GrEnumTypeDefinition> ENUM_DEFINITION =
    new GrTypeDefinitionElementType<GrEnumTypeDefinition>("enumeration definition") {
      @Override
      public GrEnumTypeDefinition createPsi(@NotNull GrTypeDefinitionStub stub) {
        return new GrEnumTypeDefinitionImpl(stub);
      }
    };
  GrStubElementType<GrTypeDefinitionStub, GrAnnotationTypeDefinition> ANNOTATION_DEFINITION =
    new GrTypeDefinitionElementType<GrAnnotationTypeDefinition>("annotation definition") {
      @Override
      public GrAnnotationTypeDefinition createPsi(@NotNull GrTypeDefinitionStub stub) {
        return new GrAnnotationTypeDefinitionImpl(stub);
      }
    };
  GrStubElementType<GrTypeDefinitionStub, GrAnonymousClassDefinition> ANONYMOUS_CLASS_DEFINITION =
    new GrTypeDefinitionElementType<GrAnonymousClassDefinition>("Anonymous class") {
      @Override
      public GrAnonymousClassDefinition createPsi(@NotNull GrTypeDefinitionStub stub) {
        return new GrAnonymousClassDefinitionImpl(stub);
      }
    };
  GrStubElementType<GrTypeDefinitionStub, GrTraitTypeDefinition> TRAIT_DEFINITION =
    new GrTypeDefinitionElementType<GrTraitTypeDefinition>("Trait definition") {
      @Override
      public GrTraitTypeDefinition createPsi(@NotNull GrTypeDefinitionStub stub) {
        return new GrTraitTypeDefinitionImpl(stub);
      }
    };

  GrStubElementType<GrTypeDefinitionStub, GrEnumConstantInitializer> ENUM_CONSTANT_INITIALIZER =
    new GrTypeDefinitionElementType<GrEnumConstantInitializer>("Enum constant initializer") {
      @Override
      public GrEnumConstantInitializer createPsi(@NotNull GrTypeDefinitionStub stub) {
        return new GrEnumConstantInitializerImpl(stub);
      }
    };

  GrStubElementType<GrFieldStub, GrEnumConstant> ENUM_CONSTANT = new GrEnumConstantElementType();
  GrStubElementType<GrFieldStub, GrField> FIELD = new GrFieldElementType();
  GrMethodElementType METHOD_DEFINITION = new GrMethodElementType("method definition") {

    @Override
    public GrMethod createPsi(@NotNull GrMethodStub stub) {
      return new GrMethodImpl(stub);
    }
  };
  GrStubElementType<GrMethodStub, GrMethod> ANNOTATION_METHOD = new GrMethodElementType("annotation method") {
    @Override
    public GrMethod createPsi(@NotNull GrMethodStub stub) {
      return new GrAnnotationMethodImpl(stub);
    }

    @Override
    public void indexStub(@NotNull GrMethodStub stub, @NotNull IndexSink sink) {
      super.indexStub(stub, sink);
      String name = stub.getName();
      sink.occurrence(GrAnnotationMethodNameIndex.KEY, name);
    }
  };

  GrReferenceListElementType<GrImplementsClause> IMPLEMENTS_CLAUSE = new GrReferenceListElementType<GrImplementsClause>("implements clause") {
    @Override
    public GrImplementsClause createPsi(@NotNull GrReferenceListStub stub) {
      return new GrImplementsClauseImpl(stub);
    }
  };
  GrReferenceListElementType<GrExtendsClause> EXTENDS_CLAUSE = new GrReferenceListElementType<GrExtendsClause>("super class clause") {
    @Override
    public GrExtendsClause createPsi(@NotNull GrReferenceListStub stub) {
      return new GrExtendsClauseImpl(stub);
    }
  };


  GroovyElementType NONE = new GroovyElementType("no token"); //not a node

  // Indicates the wrongway of parsing
  GroovyElementType WRONGWAY = new GroovyElementType("Wrong way!");
  GroovyElementType LITERAL = new GroovyElementType("Literal");
  //Packaging
  GrPackageDefinitionElementType PACKAGE_DEFINITION = new GrPackageDefinitionElementType("Package definition");

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

  EmptyStubElementType<GrEnumConstantList> ENUM_CONSTANTS = new EmptyStubElementType<GrEnumConstantList>("Enumeration constants",
                                                                                                         GroovyLanguage.INSTANCE) {
    @Override
    public GrEnumConstantList createPsi(@NotNull EmptyStub stub) {
      return new GrEnumConstantListImpl(stub);
    }
  };
  GrImportStatementElementType IMPORT_STATEMENT = new GrImportStatementElementType("Import statement");
  GroovyElementType IMPORT_ALIAS = new GroovyElementType("IMPORT_ALIAS");

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
  GroovyElementType TUPLE_ASSIGNMENT_EXPRESSION = new GroovyElementType("Tuple assignment expression");
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
  GroovyElementType COMPOSITE_LSHIFT_SIGN = new GroovyElementType("<<");
  GroovyElementType COMPOSITE_RSHIFT_SIGN = new GroovyElementType(">>");
  GroovyElementType COMPOSITE_TRIPLE_SHIFT_SIGN = new GroovyElementType(">>>");
  GroovyElementType MORE_OR_EQUALS_SIGN = new GroovyElementType(">=");
  GroovyElementType ADDITIVE_EXPRESSION = new GroovyElementType("Additive expression");
  GroovyElementType MULTIPLICATIVE_EXPRESSION = new GroovyElementType("Multiplicative expression");
  GroovyElementType POWER_EXPRESSION = new GroovyElementType("Power expression");
  GroovyElementType POWER_EXPRESSION_SIMPLE = new GroovyElementType("Simple power expression");
  GroovyElementType UNARY_EXPRESSION = new GroovyElementType("Unary expression");
  GroovyElementType CAST_EXPRESSION = new GroovyElementType("cast expression");
  GroovyElementType SAFE_CAST_EXPRESSION = new GroovyElementType("safe cast expression");
  GroovyElementType INSTANCEOF_EXPRESSION = new GroovyElementType("instanceof expression");
  GroovyElementType PATH_PROPERTY_REFERENCE = new GroovyElementType("Property reference");

  GroovyElementType PATH_METHOD_CALL = new GroovyElementType("Method call");

  GroovyElementType PATH_INDEX_PROPERTY = new GroovyElementType("Index property");
  GroovyElementType PARENTHESIZED_EXPRESSION = new GroovyElementType("Parenthesized expression");

  // Arguments
  GroovyElementType ARGUMENTS = new GroovyElementType("Arguments");
  GroovyElementType NAMED_ARGUMENT = new GroovyElementType("Compound argument");
  GroovyElementType SPREAD_ARGUMENT = new GroovyElementType("Spread argument");
  GroovyElementType ARGUMENT_LABEL = new GroovyElementType("Argument label");
  GroovyElementType REFERENCE_EXPRESSION = new GroovyElementType("Reference expressions");

  GroovyElementType NEW_EXPRESSION = new GroovyElementType("New expressions");

  GroovyElementType BUILT_IN_TYPE_EXPRESSION = new GroovyElementType("Built in type expression");

  // Lists & maps
  GroovyElementType LIST_OR_MAP = new GroovyElementType("Generalized list");
  // Type Elements
  GroovyElementType ARRAY_TYPE = new GroovyElementType("Array type");

  GroovyElementType BUILT_IN_TYPE = new GroovyElementType("Built in type");

  GroovyElementType DISJUNCTION_TYPE_ELEMENT = new GroovyElementType("Disjunction type element");

  // GStrings
  GroovyElementType GSTRING = new GroovyElementType("GString");
  GroovyElementType GSTRING_INJECTION =new GroovyElementType("Gstring injection");
  GroovyElementType GSTRING_CONTENT = new GroovyElementType("GString content element");


  GroovyElementType REGEX = new GroovyElementType("Regular expression");
  //types
  GroovyElementType REFERENCE_ELEMENT = new GroovyElementType("reference element");
  GroovyElementType ARRAY_DECLARATOR = new GroovyElementType("array declarator");

  GroovyElementType TYPE_ARGUMENTS = new GroovyElementType("type arguments", true);
  GroovyElementType TYPE_ARGUMENT = new GroovyElementType("type argument");
  EmptyStubElementType<GrTypeParameterList> TYPE_PARAMETER_LIST = new EmptyStubElementType<GrTypeParameterList>("type parameter list",
                                                                                                                GroovyLanguage.INSTANCE) {
    @Override
    public GrTypeParameterList createPsi(@NotNull EmptyStub stub) {
      return new GrTypeParameterListImpl(stub);
    }
  };

  GrStubElementType<GrTypeParameterStub, GrTypeParameter> TYPE_PARAMETER = new GrStubElementType<GrTypeParameterStub, GrTypeParameter>("type parameter") {
    @Override
    public GrTypeParameter createPsi(@NotNull GrTypeParameterStub stub) {
      return new GrTypeParameterImpl(stub);
    }

    @NotNull
    @Override
    public GrTypeParameterStub createStub(@NotNull GrTypeParameter psi, StubElement parentStub) {
      return new GrTypeParameterStub(parentStub, StringRef.fromString(psi.getName()));
    }

    @Override
    public void serialize(@NotNull GrTypeParameterStub stub, @NotNull StubOutputStream dataStream) throws IOException {
      dataStream.writeName(stub.getName());
    }

    @NotNull
    @Override
    public GrTypeParameterStub deserialize(@NotNull StubInputStream dataStream, StubElement parentStub) throws IOException {
      return new GrTypeParameterStub(parentStub, dataStream.readName());
    }
  };
  GroovyElementType TYPE_PARAMETER_EXTENDS_BOUND_LIST = new GroovyElementType("type extends list");

  GroovyElementType DEFAULT_ANNOTATION_VALUE = new GroovyElementType("default annotation value");

  GrMethodElementType CONSTRUCTOR_DEFINITION = new GrMethodElementType("constructor definition") {
    @Override
    public GrMethod createPsi(@NotNull GrMethodStub stub) {
      return new GrConstructorImpl(stub);
    }
  };

  GroovyElementType EXPLICIT_CONSTRUCTOR = new GroovyElementType("explicit constructor invokation");

  GrReferenceListElementType<GrThrowsClause> THROW_CLAUSE = new GrReferenceListElementType<GrThrowsClause>("throw clause") {
    @Override
    public GrThrowsClause createPsi(@NotNull GrReferenceListStub stub) {
      return new GrThrowsClauseImpl(stub);
    }
  };
  //annotation
  GroovyElementType ANNOTATION_ARRAY_INITIALIZER = new GroovyElementType("annotation array initializer");
  GrAnnotationArgumentListElementType ANNOTATION_ARGUMENTS = new GrAnnotationArgumentListElementType();
  GrNameValuePairElementType ANNOTATION_MEMBER_VALUE_PAIR = new GrNameValuePairElementType();

  GrStubElementType<GrAnnotationStub, GrAnnotation> ANNOTATION = new GrAnnotationElementType("annotation");
  //parameters
  EmptyStubElementType<GrParameterList> PARAMETERS_LIST = new EmptyStubElementType<GrParameterList>("parameters list",
                                                                                                    GroovyLanguage.INSTANCE) {
    @Override
    public GrParameterList createPsi(@NotNull EmptyStub stub) {
      return new GrParameterListImpl(stub);
    }
  };

  GrStubElementType<GrParameterStub, GrParameter> PARAMETER = new GrStubElementType<GrParameterStub, GrParameter>("parameter") {
    @Override
    public GrParameter createPsi(@NotNull GrParameterStub stub) {
      return new GrParameterImpl(stub);
    }

    @NotNull
    @Override
    public GrParameterStub createStub(@NotNull GrParameter psi, StubElement parentStub) {
      return new GrParameterStub(parentStub, StringRef.fromString(psi.getName()), GrStubUtils.getAnnotationNames(psi), GrStubUtils.getTypeText(
        psi.getTypeElementGroovy()), GrParameterStub.encodeFlags(psi.getInitializerGroovy() != null, psi.isVarArgs()));
    }

    @Override
    public void serialize(@NotNull GrParameterStub stub, @NotNull StubOutputStream dataStream) throws IOException {
      dataStream.writeName(stub.getName());
      GrStubUtils.writeStringArray(dataStream, stub.getAnnotations());
      GrStubUtils.writeNullableString(dataStream, stub.getTypeText());
      dataStream.writeVarInt(stub.getFlags());
    }

    @NotNull
    @Override
    public GrParameterStub deserialize(@NotNull StubInputStream dataStream, StubElement parentStub) throws IOException {
      final StringRef name = dataStream.readName();
      final String[] annotations = GrStubUtils.readStringArray(dataStream);
      final String typeText = GrStubUtils.readNullableString(dataStream);
      final int flags = dataStream.readVarInt();
      return new GrParameterStub(parentStub, name, annotations, typeText, flags);
    }
  };

  EmptyStubElementType<GrTypeDefinitionBody> CLASS_BODY = new EmptyStubElementType<GrTypeDefinitionBody>("class block",
                                                                                                         GroovyLanguage.INSTANCE) {
      @Override
      public GrTypeDefinitionBody createPsi(@NotNull EmptyStub stub) {
        return new GrTypeDefinitionBodyBase.GrClassBody(stub);
      }
    };

  EmptyStubElementType<GrEnumDefinitionBody> ENUM_BODY = new EmptyStubElementType<GrEnumDefinitionBody>("enum block",
                                                                                                        GroovyLanguage.INSTANCE) {
    @Override
    public GrEnumDefinitionBody createPsi(@NotNull EmptyStub stub) {
      return new GrTypeDefinitionBodyBase.GrEnumBody(stub);
    }
  };
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

  EmptyStubElementType<GrVariableDeclaration> VARIABLE_DEFINITION_ERROR = new EmptyStubElementType<GrVariableDeclaration>("variable definitions with errors",
                                                                                                                          GroovyLanguage.INSTANCE) {
    @Override
    public boolean shouldCreateStub(ASTNode node) {
      return false;
    }

    @Override
    public GrVariableDeclaration createPsi(@NotNull EmptyStub stub) {
      throw new UnsupportedOperationException("Not implemented");
    }
  };
  GrVariableDeclarationElementType VARIABLE_DEFINITION = new GrVariableDeclarationElementType();
  GroovyElementType TUPLE_DECLARATION = new GroovyElementType("tuple declaration");
  GroovyElementType TUPLE = new GroovyElementType("tuple");

  GrVariableElementType VARIABLE = new GrVariableElementType();

  //modifiers
  GrStubElementType<GrModifierListStub, GrModifierList> MODIFIERS = new GrModifierListElementType("modifier list");

  //types
  GroovyElementType CLASS_TYPE_ELEMENT = new GroovyElementType("class type element"); //node

  IStubFileElementType GROOVY_FILE = new GrStubFileElementType(GroovyLanguage.INSTANCE);
}
