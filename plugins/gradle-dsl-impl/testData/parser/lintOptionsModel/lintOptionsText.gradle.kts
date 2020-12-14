android {
  lintOptions {
    isAbortOnError = true
    isAbsolutePaths = false
    check("check-id-1", "check-id-2")
    isCheckAllWarnings = true
    isCheckReleaseBuilds = false
    disable("disable-id-1", "disable-id-2")
    enable("enable-id-1", "enable-id-2")
    error("error-id-1", "error-id-2")
    isExplainIssues = true
    fatal("fatal-id-1", "fatal-id-2")
    htmlOutput = file("html.output")
    htmlReport = false
    ignore("ignore-id-1", "ignore-id-2")
    isIgnoreWarnings = true
    lintConfig = file("lint.config")
    isNoLines = false
    isQuiet = true
    isShowAll = false
    textOutput(file("text.output"))
    textReport = true
    warning("warning-id-1", "warning-id-2")
    isWarningsAsErrors = false
    xmlOutput = file("xml.output")
    xmlReport = true
  }
}
