load("//tools/base/bazel:bazel.bzl", "iml_module")

iml_module(
    name = "vcs-impl",
    srcs = ["src"],
    iml_files = ["vcs-impl.iml"],
    tags = ["managed"],
    # do not sort: must match IML order
    test_runtime_deps = [
        "//tools/idea/xml/xml-structure-view-impl",
        "//tools/idea/spellchecker",
        "//tools/idea/images",
    ],
    test_srcs = ["testSrc"],
    test_tags = ["manual"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    exports = [
        "//tools/idea/platform/vcs-api",
        "//tools/idea/platform/diff-impl",
    ],
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
        "//tools/idea/platform/vcs-api[module]",
        "//tools/idea/platform/platform-api[module]",
        "//tools/idea/platform/platform-impl[module]",
        "//tools/idea/.idea/libraries:commons-codec",
        "//tools/idea/platform/lang-api[module]",
        "//tools/idea/jps/model-serialization:jps-model-serialization[module]",
        "//tools/idea/.idea/libraries:Guava",
        "//tools/idea/platform/lang-impl[module]",
        "//tools/idea/.idea/libraries:JUnit4[test]",
        "//tools/idea/platform/testFramework[module, test]",
        "//tools/idea/.idea/libraries:gson",
        "//tools/idea/platform/diff-impl[module]",
        "//tools/idea/platform/vcs-log/api:vcs-log-api[module]",
        "//tools/idea/.idea/libraries:swingx",
    ],
)
