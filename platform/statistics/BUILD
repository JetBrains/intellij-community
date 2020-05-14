load("//tools/base/bazel:bazel.bzl", "iml_module")

# managed by go/iml_to_build
iml_module(
    name = "intellij.platform.statistics",
    srcs = ["src"],
    iml_files = ["intellij.platform.statistics.iml"],
    resources = ["resources"],
    visibility = ["//visibility:public"],
    exports = ["//tools/idea/platform/statistics/uploader:intellij.platform.statistics.uploader"],
    # do not sort: must match IML order
    runtime_deps = [
        "//tools/idea/.idea/libraries:delight-rhino-sandbox",
        "//tools/idea/.idea/libraries:rhino",
        "//tools/idea/.idea/libraries:netty-handler-proxy",
    ],
    # do not sort: must match IML order
    deps = [
        "//tools/idea/platform/core-impl:intellij.platform.core.impl[module]",
        "//tools/idea/.idea/libraries:kotlin-stdlib-jdk8",
        "//tools/idea/.idea/libraries:gson",
        "//tools/idea/.idea/libraries:Log4J",
        "//tools/idea/.idea/libraries:jetbrains-annotations",
        "//tools/idea/.idea/libraries:JDOM",
        "//tools/idea/.idea/libraries:Guava",
        "//tools/idea/platform/platform-api:intellij.platform.ide[module]",
        "//tools/idea/platform/statistics/uploader:intellij.platform.statistics.uploader[module]",
    ],
)
