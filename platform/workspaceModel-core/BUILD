load("//tools/base/bazel:bazel.bzl", "iml_module")

# managed by go/iml_to_build
iml_module(
    name = "intellij.platform.workspaceModel.core",
    srcs = ["src"],
    iml_files = ["intellij.platform.workspaceModel.core.iml"],
    test_srcs = ["test"],
    test_tags = ["manual"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "//tools/idea/.idea/libraries:kotlin-stdlib-jdk8",
        "//tools/idea/.idea/libraries:Guava",
        "//tools/idea/.idea/libraries:Slf4j",
        "//tools/idea/.idea/libraries:kotlin-reflect",
        "//tools/idea/platform/util:intellij.platform.util[module]",
        "//tools/idea/.idea/libraries:JUnit4[test]",
        "//tools/idea/.idea/libraries:Kryo",
        "//tools/idea/.idea/libraries:Objenesis",
    ],
)
