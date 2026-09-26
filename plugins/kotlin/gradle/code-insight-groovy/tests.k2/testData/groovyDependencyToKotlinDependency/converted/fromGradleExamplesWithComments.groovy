// from https://docs.gradle.org/current/userguide/declaring_dependencies_basics.html

runtimeOnly group: 'org.springframework',/*aaa*/ name: 'spring-core', version: '2.5'
// bbb
runtimeOnly 'org.springframework:spring-core:2.5',//ccc
            'org.springframework:spring-aop:2.5'
//dd
runtimeOnly(
        //ee
        [group: 'org.springframework', name: 'spring-core', version: '2.5'],
        [group: 'org.springframework', name: 'spring-aop', version: '2.5']
)
//ff
runtimeOnly('org.hibernate:hibernate:3.0.5') {
    transitive = true //gg
}
//h
runtimeOnly group: 'org.hibernate', name: 'hibernate', version: '3.0.5', transitive: true
runtimeOnly(group: 'org.hibernate', name: 'hibernate', version: '3.0.5') {
    transitive = true
}
//ii