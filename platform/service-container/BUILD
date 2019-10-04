load("//tools/base/bazel:bazel.bzl", "iml_module")

# managed by go/iml_to_build
iml_module(
    name = "intellij.platform.serviceContainer",
    srcs = ["src"],
    iml_files = ["intellij.platform.serviceContainer.iml"],
    test_srcs = ["testSrc"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "//tools/idea/.idea/libraries:kotlin-stdlib-jdk8",
        "//tools/idea/platform/core-api:intellij.platform.core[module]",
        "//tools/idea/platform/core-impl:intellij.platform.core.impl[module]",
        "//tools/idea/platform/util-ex:intellij.platform.util.ex[module]",
        "//tools/idea/platform/projectModel-impl:intellij.platform.projectModel.impl[module]",
        "//tools/idea/platform/bootstrap:intellij.platform.bootstrap[module]",
        "//tools/idea/.idea/libraries:JUnit4[test]",
        "//tools/idea/.idea/libraries:assertJ[test]",
    ],
)
