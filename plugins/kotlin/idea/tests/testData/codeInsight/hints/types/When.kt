// MODE: all
val x<# [:  [jar://kotlin-stdlib-sources.jar!/kotlin/Primitives.kt:*]Int] #> = run {
    when (true) {
        true -> 1
        false -> 0
    }
}