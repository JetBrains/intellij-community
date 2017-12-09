load("//tools/base/bazel:bazel.bzl", "iml_module")

iml_module(
    name = "java-decompiler-plugin",
    srcs = ["src"],
    iml_files = ["java-decompiler-plugin.iml"],
    tags = ["managed"],
    # do not sort: must match IML order
    test_runtime_deps = [
        "//tools/idea/java/execution/impl:execution-impl",
        "//tools/idea/platform/external-system-impl",
        "//tools/idea/java/compiler/instrumentation-util-8",
    ],
    test_srcs = ["test"],
    test_tags = ["manual"],
    visibility = ["//visibility:public"],
    exports = ["//tools/idea/plugins/java-decompiler/engine:java-decompiler-engine"],
    # do not sort: must match IML order
    runtime_deps = [
        "//tools/idea/RegExpSupport",
        "//tools/idea/xml/xml-analysis-impl",
        "//tools/idea/platform/xdebugger-impl",
        "//tools/idea/platform/lvcs-impl",
        "//tools/idea/platform/credential-store",
        "//tools/idea/platform/configuration-store-impl",
        "//tools/idea/platform/built-in-server",
        "//tools/idea/platform/tasks-platform-impl",
        "//tools/idea/json",
        "//tools/idea/xml/relaxng",
        "//tools/idea/xml/dom-impl",
    ],
    # do not sort: must match IML order
    deps = [
        "//tools/idea/plugins/java-decompiler/engine:java-decompiler-engine[module]",
        "//tools/idea/platform/lang-api[module]",
        "//tools/idea/java/java-psi-api[module]",
        "//tools/idea/java/java-psi-impl[module]",
        "//tools/idea/platform/util[module]",
        "//tools/idea/platform/platform-impl[module]",
        "//tools/idea/platform/core-api[module]",
        "//tools/idea:jps-builders_and_others[module, test]",
    ],
)
