load("//tools/base/bazel:bazel.bzl", "iml_module")

iml_module(
    name = "structure-view-impl",
    srcs = ["src"],
    iml_files = ["structure-view-impl.iml"],
    tags = ["managed"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    exports = [
        "//tools/idea/platform/editor-ui-api",
        "//tools/idea/platform/util",
        "//tools/idea/platform/extensions",
        "//tools/idea/platform/core-api",
        "//tools/idea/.idea/libraries:picocontainer",
        "//tools/idea/platform/platform-resources-en",
        "//tools/idea/platform/projectModel-api",
    ],
    # do not sort: must match IML order
    deps = [
        "//tools/idea/platform/editor-ui-api[module]",
        "//tools/idea/platform/util[module]",
        "//tools/idea/platform/extensions[module]",
        "//tools/idea/platform/core-api[module]",
        "//tools/idea/.idea/libraries:picocontainer",
        "//tools/idea/platform/bootstrap[module]",
        "//tools/idea/platform/platform-resources-en[module]",
        "//tools/idea/platform/icons[module]",
        "//tools/idea/.idea/libraries:automaton",
        "//tools/idea/platform/projectModel-api[module]",
        "//tools/idea/platform/platform-api[module]",
    ],
)
