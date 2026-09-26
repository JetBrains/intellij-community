// PSI_ELEMENT: org.jetbrains.kotlin.analysis.decompiled.light.classes.KtLightMethodForDecompiledDeclaration
// OPTIONS: usages
// PSI_ELEMENT_AS_TITLE: "fun processRequest(): String"
package client;

import server.RequestProcessor;

class JClient {
    String s = RequestProcessor.<caret>processRequest();

    String sendRequest() {
        return RequestProcessor.processRequest();
    }
}
