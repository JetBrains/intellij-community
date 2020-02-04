load("//tools/base/bazel:bazel.bzl", "iml_module")

# managed by go/iml_to_build
iml_module(
    name = "intellij.platform.debugger",
    srcs = ["src"],
    iml_files = ["intellij.platform.debugger.iml"],
    visibility = ["//visibility:public"],
    exports = ["//tools/idea/platform/lang-api:intellij.platform.lang"],
    # do not sort: must match IML order
    runtime_deps = [
        "//tools/idea/.idea/libraries:delight-rhino-sandbox",
        "//tools/idea/.idea/libraries:rhino",
        "//tools/idea/.idea/libraries:netty-handler-proxy",
    ],
    # do not sort: must match IML order
    deps = [
        "//tools/idea/platform/lang-api:intellij.platform.lang[module]",
        "//tools/idea/platform/util:intellij.platform.util.ui[module]",
    ],
)
