load("//tools/base/bazel:bazel.bzl", "iml_module")

iml_module(
    name = "junit5_rt_tests",
    iml_files = ["junit5_rt_tests.iml"],
    tags = ["managed"],
    # do not sort: must match IML order
    test_runtime_deps = [
        "//tools/idea:json",
        "//tools/idea:credential-store",
        "//tools/idea/plugins/maven:maven2-server-impl",
    ],
    test_srcs = ["test"],
    test_tags = ["manual"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "//tools/idea/plugins/junit5_rt/lib:junit-jupiter-api-5.0.0-M4[test]",
        "//tools/idea/plugins/junit5_rt[module]",
        "//tools/idea/.idea/libraries:junit5_rt",
        "//tools/idea/.idea/libraries:opentest4j",
        "//tools/idea:compiler-impl_and_others[module, test]",
        "//tools/idea:execution-impl[module, test]",
        "//tools/idea/plugins/junit[module, test]",
        "//tools/idea/plugins/maven[module, test]",
        "//tools/idea:java-tests[module, test]",
    ],
)
