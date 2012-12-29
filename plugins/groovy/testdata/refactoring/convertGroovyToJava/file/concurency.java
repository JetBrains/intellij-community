public class concurency extends groovy.lang.Script {
public static void main(java.lang.String[] args) {
new concurency(new groovy.lang.Binding(args)).run();
}

public java.lang.Object run() {


final java.util.concurrent.atomic.AtomicInteger counter = new java.util.concurrent.atomic.AtomicInteger();



java.lang.Thread th = org.codehaus.groovy.runtime.DefaultGroovyStaticMethods.start(null, new groovy.lang.Closure<java.lang.Void>(this, this) {
public void doCall(java.lang.Object it) {
for(java.lang.Integer i : new groovy.lang.IntRange(1, 8)){
org.codehaus.groovy.runtime.DefaultGroovyStaticMethods.sleep(null, 30);
out("thread loop " + java.lang.String.valueOf(i));
counter.incrementAndGet();
}

}

public void doCall() {
doCall(null);
}

});

for(java.lang.Integer j : new groovy.lang.IntRange(1, 4)){
org.codehaus.groovy.runtime.DefaultGroovyStaticMethods.sleep(null, 50);
out("main loop " + java.lang.String.valueOf(j));
counter.incrementAndGet();
}


th.join();

assert counter.get() == 12;
return null;

}

public synchronized void out(groovy.lang.GString message) {
println(message);
}

public concurency(groovy.lang.Binding binding) {
super(binding);
}
public concurency() {
super();
}
}
