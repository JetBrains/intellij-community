load("//tools/base/bazel:bazel.bzl", "iml_module")

iml_module(
    name = "junit5_rt_tests",
    iml_files = ["junit5_rt_tests.iml"],
    tags = ["managed"],
    test_srcs = ["test"],
    test_tags = ["manual"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "//tools/idea/plugins/junit5_rt/lib:junit-jupiter-api-5.0.0-M4[test]",
        "//tools/idea/plugins/junit5_rt[module]",
        "//tools/idea/.idea/libraries:junit5_rt",
        "//tools/idea/.idea/libraries:opentest4j",
        "//tools/idea:compiler-impl_and_others[module]",
        "//tools/idea/plugins/junit[module, test]",
        "//tools/idea/plugins/maven:maven_and_others[module]",
        "//tools/idea:java-tests[module, test]",
    ],
)
