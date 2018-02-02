load("//tools/base/bazel:bazel.bzl", "iml_module")

iml_module(
    name = "google-app-engine",
    # do not sort: must match IML order
    srcs = [
        "source",
        "gen",
    ],
    iml_files = ["google-app-engine.iml"],
    resources = ["resources"],
    tags = ["managed"],
    test_srcs = ["testSource"],
    test_tags = ["manual"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    runtime_deps = [
        "//tools/idea/platform/credential-store",
        "//tools/idea/platform/tasks-platform-impl",
        "//tools/idea/json",
        "//tools/idea/xml/relaxng",
        "//tools/idea/java/compiler/instrumentation-util-8",
    ],
    # do not sort: must match IML order
    deps = [
        "//tools/idea/java/execution/openapi:execution-openapi[module]",
        "//tools/idea/platform/platform-api[module]",
        "//tools/idea/platform/util[module]",
        "//tools/idea/platform/lang-api[module]",
        "//tools/idea/java/openapi[module]",
        "//tools/idea/java/debugger/openapi:debugger-openapi[module]",
        "//tools/idea:jps-builders_and_others[module]",
        "//tools/idea/.idea/libraries:JUnit4[test]",
        "//tools/idea/xml/impl:xml[module, test]",
        "//tools/idea/plugins/google-app-engine/runtime:appEngine-runtime[module]",
        "//tools/idea/plugins/google-app-engine/jps-plugin:google-app-engine-jps-plugin[module]",
        "//tools/idea/xml/dom-openapi[module]",
        "//tools/idea:java-impl[module]",
        "//tools/idea/java/remote-servers/api:remote-servers-java-api[module]",
        "//tools/idea/plugins/maven[module]",
    ],
)
