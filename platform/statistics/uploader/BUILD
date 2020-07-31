load("//tools/base/bazel:bazel.bzl", "iml_module")

# managed by go/iml_to_build
iml_module(
    name = "intellij.platform.statistics.uploader",
    srcs = ["src"],
    iml_files = ["intellij.platform.statistics.uploader.iml"],
    visibility = ["//visibility:public"],
    exports = ["//tools/idea/platform/statistics/config:intellij.platform.statistics.config"],
    # do not sort: must match IML order
    deps = [
        "//tools/idea/.idea/libraries:jetbrains-annotations",
        "//tools/idea/.idea/libraries:kotlin-stdlib-jdk8",
        "//tools/idea/.idea/libraries:gson",
        "//tools/idea/.idea/libraries:http-client",
        "//tools/idea/.idea/libraries:Log4J",
        "//tools/idea/platform/statistics/config:intellij.platform.statistics.config[module]",
    ],
)
