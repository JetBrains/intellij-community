package transitiveStory.apiJvm.jbeginning;

import playground.SomeUsefulInfoKt;

public class <!LINE_MARKER("descr='Is subclassed by JApiCallerInJVM18 (transitiveStory.bottomActual.jApiCall) Jvm18JApiInheritor (transitiveStory.bottomActual.apiCall) Press ... to navigate'")!>JavaApiContainer<!> {
    private String privateJavaDeclaration = "I'm a private string from `" + SomeUsefulInfoKt.getModuleName() +
            "` and shall be never visible to the others.";

    String packageVisibleJavaDeclaration = "I'm a packag1e visible string from `" + SomeUsefulInfoKt.getModuleName() +
            "` and shall be never visible to the other modules.";

    protected String protectedJavaDeclaration = "I'm a protected string from `" + SomeUsefulInfoKt.getModuleName() +
            "` and shall be never visible to the other modules except my subclasses.";

    public String publicJavaDeclaration = "I'm a public string from `" + SomeUsefulInfoKt.getModuleName() +
            "` and shall be visible to the other modules.";

    public static String publicStaticJavaDeclaration = "I'm a public static string from `" + SomeUsefulInfoKt.getModuleName() +
            "` and shall be visible to the other modules even without instantiation of `JavaApiContainer` class.";
}
