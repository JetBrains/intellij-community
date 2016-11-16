load("//tools/base/bazel:bazel.bzl", "iml_module")

iml_module(
    name = "idea.vcs-tests",
    srcs = ["src"],
    tags = ["managed"],
    test_srcs = ["testSrc"],
    test_tags = ["manual"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "//tools/idea/.idea/libraries:KotlinJavaRuntime",
        "//tools:idea.platform-impl_and_others[module]",
        "//tools:idea.vcs-api[module]",
        "//tools:idea.vcs-api-core[module]",
        "//tools:idea.core-api[module]",
        "//tools/idea/.idea/libraries:JUnit4",
        "//tools:idea.platform-api[module]",
    ],
)
