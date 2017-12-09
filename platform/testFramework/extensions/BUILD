load("//tools/base/bazel:bazel.bzl", "iml_module")

iml_module(
    name = "testExtensions",
    iml_files = ["testExtensions.iml"],
    tags = ["managed"],
    # do not sort: must match IML order
    test_runtime_deps = [
        "//tools/idea/RegExpSupport",
        "//tools/idea/platform/lvcs-impl",
        "//tools/idea/xml/xml-structure-view-impl",
        "//tools/idea/spellchecker",
        "//tools/idea/xml/relaxng",
        "//tools/idea/json",
        "//tools/idea/images",
        "//tools/idea/platform/tasks-platform-impl",
        "//tools/idea/platform/credential-store",
        "//tools/idea/platform/configuration-store-impl",
        "//tools/idea/platform/built-in-server",
    ],
    test_srcs = ["src"],
    test_tags = ["manual"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    exports = [
        "//tools/idea/.idea/libraries:JDOM",
        "//tools/idea/.idea/libraries:assertJ",
    ],
    # do not sort: must match IML order
    deps = [
        "//tools/idea/.idea/libraries:KotlinJavaRuntime[test]",
        "//tools/idea/platform/util[module, test]",
        "//tools/idea/platform/testFramework[module, test]",
        "//tools/idea/.idea/libraries:JDOM[test]",
        "//tools/idea/.idea/libraries:assertJ[test]",
        "//tools/idea/.idea/libraries:memoryfilesystem[test]",
        "//tools/idea/.idea/libraries:snakeyaml[test]",
    ],
)
