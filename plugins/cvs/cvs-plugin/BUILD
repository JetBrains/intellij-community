load("//tools/base/bazel:bazel.bzl", "iml_module")

iml_module(
    name = "cvs-plugin",
    srcs = ["src"],
    iml_files = ["cvs-plugin.iml"],
    tags = ["managed"],
    # do not sort: must match IML order
    test_runtime_deps = [
        "//tools/idea/xml/xml-structure-view-impl",
        "//tools/idea/spellchecker",
        "//tools/idea/images",
    ],
    visibility = ["//visibility:public"],
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
        "//tools/idea/plugins/cvs/cvs-core[module]",
        "//tools/idea/platform/vcs-api[module]",
        "//tools/idea/plugins/cvs:javacvs-src[module]",
        "//tools/idea/platform/vcs-impl[module]",
        "//tools/idea/platform/lang-api[module]",
        "//tools/idea/plugins/cvs:smartcvs-src[module]",
        "//tools/idea/.idea/libraries:microba",
        "//tools/idea/platform/platform-impl[module]",
    ],
)
