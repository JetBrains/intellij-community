# managed by go/iml_to_build
java_import(
    name = "coverage-report",
    jars = ["coverage-report.jar"],
    visibility = ["//visibility:public"],
)

# managed by go/iml_to_build
java_import(
    name = "coverage-report-idea",
    jars = ["coverage-report-idea.jar"],
    visibility = ["//visibility:public"],
)

# managed by go/iml_to_build
java_import(
    name = "freemarker",
    jars = ["freemarker.jar"],
    visibility = ["//visibility:public"],
)

# managed by go/iml_to_build
java_import(
    name = "jacocoant",
    jars = ["jacocoant.jar"],
    # b/112891050
    neverlink = select({
        "//tools/base/bazel:coverage": 1,
        "//conditions:default": 0,
    }),
    visibility = ["//visibility:public"],
)

# managed by go/iml_to_build
java_import(
    name = "jacocoagent",
    jars = ["jacocoagent.jar"],
    # b/112891050
    neverlink = select({
        "//tools/base/bazel:coverage": 1,
        "//conditions:default": 0,
    }),
    visibility = ["//visibility:public"],
)
