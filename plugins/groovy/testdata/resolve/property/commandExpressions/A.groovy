class GClass {
 def say = { def msg -> this }
 def to = { def person -> this }

 def use() {
  say "Hello" t<ref>o "everyone" // 'to' is not resolved
 }
}