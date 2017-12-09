load("//tools/base/bazel:bazel.bzl", "iml_module")

iml_module(
    name = "IntelliLang-tests",
    iml_files = ["IntelliLang-tests.iml"],
    tags = ["managed"],
    test_srcs = ["test"],
    test_tags = ["manual"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    runtime_deps = [
        "//tools/idea/platform/credential-store",
        "//tools/idea/platform/configuration-store-impl",
        "//tools/idea/platform/tasks-platform-impl",
        "//tools/idea/json",
        "//tools/idea/xml/relaxng",
        "//tools/idea/java/compiler/instrumentation-util-8",
        "//tools/idea/java/execution/impl:execution-impl",
        "//tools/idea/platform/external-system-impl",
    ],
    # do not sort: must match IML order
    deps = [
        "//tools/idea:jps-builders_and_others[module]",
        "//tools/idea/plugins/IntelliLang[module]",
        "//tools/idea/plugins/IntelliLang:IntelliLang-xml[module]",
        "//tools/idea/xml/impl:xml[module]",
        "//tools/idea/plugins/IntelliLang:IntelliLang-java[module]",
    ],
)
