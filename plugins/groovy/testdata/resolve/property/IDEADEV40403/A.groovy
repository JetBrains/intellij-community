class MyScript extends Script {
	def foo

	def run() {
	  foo.binding = 5
	  println bin<caret>ding // editor thinks that "binding" refers to foo.binding
	}
}
