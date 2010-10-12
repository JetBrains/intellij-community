package org.jetbrains.javafx.lang.parser;

import com.intellij.psi.tree.IFileElementType;
import com.intellij.psi.tree.IStubFileElementType;
import org.jetbrains.javafx.JavaFxLanguage;
import org.jetbrains.javafx.lang.psi.*;
import org.jetbrains.javafx.lang.psi.impl.stubs.types.*;
import org.jetbrains.javafx.lang.psi.stubs.*;

/**
 * @author peter
 */
public interface JavaFxStubElementTypes {
  // stubs
  JavaFxStubElementType<JavaFxClassStub, JavaFxClassDefinition> CLASS_DEFINITION = new JavaFxClassElementType();
  JavaFxStubElementType<JavaFxFunctionStub, JavaFxFunctionDefinition> FUNCTION_DEFINITION = new JavaFxFunctionElementType();
  JavaFxStubElementType<JavaFxVariableStub, JavaFxVariableDeclaration> VARIABLE_DECLARATION = new JavaFxVariableElementType();
  JavaFxStubElementType<JavaFxParameterListStub, JavaFxParameterList> PARAMETER_LIST = new JavaFxParameterListElementType();
  JavaFxStubElementType<JavaFxParameterStub, JavaFxParameter> FORMAL_PARAMETER = new JavaFxParameterElementType();
  JavaFxStubElementType<JavaFxPackageDefinitionStub, JavaFxPackageDefinition> PACKAGE_DEFINITION = new JavaFxPackageDefinitionElementType();
  JavaFxStubElementType<JavaFxImportListStub, JavaFxImportList> IMPORT_LIST = new JavaFxImportListElementType();
  JavaFxStubElementType<JavaFxSignatureStub, JavaFxSignature> FUNCTION_SIGNATURE = new JavaFxSignatureElementType();

  IFileElementType FILE = new IStubFileElementType(JavaFxLanguage.INSTANCE);
}
