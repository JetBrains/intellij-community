// CHECK_SYMBOL_NAMES
// HIGHLIGHTER_ATTRIBUTES_KEY
// ALLOW_ERRORS

@file:ScriptFileLocation("MY_CUSTOM_LOCATION")

println("Hello from ${MY_CUSTOM_LOCATION.path}")
println("I'm not $__FILE__") //error
