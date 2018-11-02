load("//tools/base/bazel:bazel.bzl", "iml_module")

# managed by go/iml_to_build
iml_module(
    name = "intellij.java.googleAppEngine",
    # do not sort: must match IML order
    srcs = [
        "source",
        "gen",
    ],
    iml_files = ["intellij.java.googleAppEngine.iml"],
    resources = ["resources"],
    test_srcs = ["testSource"],
    test_tags = ["manual"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    runtime_deps = [
        "//tools/idea/xml/relaxng:intellij.relaxng",
        "//tools/idea/platform/tasks-platform-impl:intellij.platform.tasks.impl",
        "//tools/idea/json:intellij.json",
        "//tools/idea/platform/credential-store:intellij.platform.credentialStore",
        "//tools/idea/java/compiler/instrumentation-util-8:intellij.java.compiler.instrumentationUtil.java8",
        "//tools/idea/jvm/jvm-analysis-impl:intellij.jvm.analysis.impl",
        "//tools/idea/plugins/maven/error-prone-compiler:intellij.maven.errorProne.compiler",
        "//tools/idea/plugins/IntelliLang/intellilang-jps-plugin:intellij.java.langInjection.jps",
    ],
    # do not sort: must match IML order
    deps = [
        "//tools/idea/java/execution/openapi:intellij.java.execution[module]",
        "//tools/idea/platform/platform-api:intellij.platform.ide[module]",
        "//tools/idea/platform/util:intellij.platform.util[module]",
        "//tools/idea/platform/lang-api:intellij.platform.lang[module]",
        "//tools/idea/java/openapi:intellij.java[module]",
        "//tools/idea/java/debugger/openapi:intellij.java.debugger[module]",
        "//tools/idea:intellij.platform.jps.build_and_others[module]",
        "//tools/idea/.idea/libraries:JUnit4[test]",
        "//tools/idea/xml/impl:intellij.xml.impl[module, test]",
        "//tools/idea/plugins/google-app-engine/runtime:intellij.java.googleAppEngine.runtime[module]",
        "//tools/idea/plugins/google-app-engine/jps-plugin:intellij.java.googleAppEngine.jps[module]",
        "//tools/idea/xml/dom-openapi:intellij.xml.dom[module]",
        "//tools/idea:intellij.java.impl[module]",
        "//tools/idea/java/remote-servers/api:intellij.java.remoteServers[module]",
        "//tools/idea/plugins/maven:intellij.maven[module]",
    ],
)
