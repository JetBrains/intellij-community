load("//tools/base/bazel:bazel.bzl", "iml_module")

iml_module(
    name = "protocol-reader-runtime",
    srcs = ["src"],
    iml_files = ["protocol-reader-runtime.iml"],
    tags = ["managed"],
    visibility = ["//visibility:public"],
    exports = ["//tools/idea/.idea/libraries:gson"],
    # do not sort: must match IML order
    deps = [
        "//tools/idea/.idea/libraries:gson",
        "//tools/idea/platform/util[module]",
        "//tools/idea:platform-impl_and_others[module]",
        "//tools/idea/.idea/libraries:Netty",
    ],
)
