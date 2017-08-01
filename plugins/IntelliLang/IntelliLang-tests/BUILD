load("//tools/base/bazel:bazel.bzl", "iml_module")

iml_module(
    name = "IntelliLang-tests",
    iml_files = ["IntelliLang-tests.iml"],
    tags = ["managed"],
    test_srcs = ["test"],
    test_tags = ["manual"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "//tools/idea:compiler-impl_and_others[module]",
        "//tools/idea/plugins/IntelliLang[module]",
        "//tools/idea/plugins/IntelliLang:IntelliLang-xml[module]",
        "//tools/idea:platform-impl_and_others[module]",
        "//tools/idea/plugins/IntelliLang:IntelliLang-java[module]",
    ],
)
