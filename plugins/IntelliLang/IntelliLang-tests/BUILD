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
        "//tools/idea:credential-store",
        "//tools/idea:configuration-store-impl",
        "//tools/idea:tasks-platform-impl",
        "//tools/idea:json",
        "//tools/idea:relaxng",
        "//tools/idea/java/compiler/instrumentation-util-8",
        "//tools/idea:execution-impl",
        "//tools/idea:external-system-impl",
    ],
    # do not sort: must match IML order
    deps = [
        "//tools/idea:jps-builders_and_others[module]",
        "//tools/idea/plugins/IntelliLang[module]",
        "//tools/idea/plugins/IntelliLang:IntelliLang-xml[module]",
        "//tools/idea:xml[module]",
        "//tools/idea/plugins/IntelliLang:IntelliLang-java[module]",
    ],
)
