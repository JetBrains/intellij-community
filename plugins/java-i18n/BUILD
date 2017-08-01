load("//tools/base/bazel:bazel.bzl", "iml_module")

iml_module(
    name = "java-i18n",
    srcs = ["src"],
    iml_files = ["java-i18n.iml"],
    resources = ["resources"],
    tags = ["managed"],
    test_srcs = ["testSrc"],
    test_tags = ["manual"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    exports = [
        "//tools/idea/plugins/properties",
        "//tools/idea:java-impl",
    ],
    # do not sort: must match IML order
    deps = [
        "//tools/idea/platform/lang-api[module]",
        "//tools/idea/java/openapi[module]",
        "//tools/idea/plugins/properties[module]",
        "//tools/idea:platform-impl_and_others[module]",
        "//tools/idea:java-impl[module]",
        "//tools/idea/java/jsp-base-openapi[module]",
        "//tools/idea/java/jsp-openapi[module]",
        "//tools/idea:compiler-impl_and_others[module]",
        "//tools/idea/plugins/properties/properties-psi-api[module]",
        "//tools/idea/plugins/properties/properties-psi-impl[module]",
    ],
)
