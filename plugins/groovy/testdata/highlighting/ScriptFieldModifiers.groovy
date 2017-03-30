import groovy.transform.Field

@Field def s0 = 1
@Field protected s1 = 1
@Field private s2 = 1
@Field static s3 = 1
@Field abstract s4 = 1
@Field final s5 = 1
@Field native s6 = 1
@Field synchronized s7 = 1
@Field strictfp s8 = 1
@Field transient s9 = 1
@Field volatile s10 = 1

<error descr="Illegal combination of modifiers">@Field public private</error> a
<error descr="Illegal combination of modifiers">@Field private protected</error> b
<error descr="Illegal combination of modifiers">@Field protected public</error> c
<error descr="Illegal combination of modifiers 'volatile' and 'final'">@Field volatile final</error> g

<error descr="Duplicate modifier 'public'">@Field public public</error> d
