load("//tools/base/bazel:bazel.bzl", "iml_module")

iml_module(
    name = "copyright",
    srcs = ["src"],
    iml_files = ["copyright.iml"],
    tags = ["managed"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "//tools/idea/java/jsp-openapi[module]",
        "//tools/idea/xml/openapi:xml-openapi[module]",
        "//tools/idea/platform/lang-api[module]",
        "//tools/idea:platform-impl_and_others[module]",
        "//tools/idea/java/openapi[module]",
        "//tools/idea/.idea/libraries:Velocity",
        "//tools/idea/.idea/libraries:KotlinJavaRuntime",
    ],
)
