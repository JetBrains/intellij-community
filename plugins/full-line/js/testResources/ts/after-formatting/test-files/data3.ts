import type Watcher from './watcher'
import config from '../config'
import Dep, { cleanupDeps } from './dep'
import { callHook, activateChildComponent } from '../instance/lifecycle'
import { warn, nextTick, devtools, inBrowser, isIE } from '../util/index'
import type { Component } from 'types/component'
export const MAX_UPDATE_COUNT = 100
const queue: Array<Watcher> = []
const activatedChildren: Array<Component> = []
let has: { [key: number]: true | undefined | null } = {}
let circular: { [key: number]: number } = {}
let waiting = false
let flushing = false
let index = 0
function resetSchedulerState() {
index = queue.length = activatedChildren.length = 0
has = {}
if (__DEV__) {
circular = {}
}
waiting = flushing = false
}
export let currentFlushTimestamp = 0
let getNow: () => number = Date.now
if (inBrowser && !isIE) {
const performance = window.performance
if (
performance &&
typeof performance.now === 'function' &&
getNow() > document.createEvent('Event').timeStamp
) {
getNow = () => performance.now()
}
}
const sortCompareFn = (a: Watcher, b: Watcher): number => {
if (a.post) {
if (!b.post) return 1
} else if (b.post) {
return -1
}
return a.id - b.id
}
function flushSchedulerQueue() {
currentFlushTimestamp = getNow()
flushing = true
let watcher, id
queue.sort(sortCompareFn)
for (index = 0; index < queue.length; index++) {
watcher = queue[index]
if (watcher.before) {
watcher.before()
}
id = watcher.id
has[id] = null
watcher.run()
if (__DEV__ && has[id] != null) {
circular[id] = (circular[id] || 0) + 1
if (circular[id] > MAX_UPDATE_COUNT) {
warn(
'You may have an infinite update loop ' +
(watcher.user
? `in watcher with expression "${watcher.expression}"`
: `in a component render function.`),
watcher.vm
)
break
}
}
}
const activatedQueue = activatedChildren.slice()
const updatedQueue = queue.slice()
resetSchedulerState()
callActivatedHooks(activatedQueue)
callUpdatedHooks(updatedQueue)
cleanupDeps()
if (devtools && config.devtools) {
devtools.emit('flush')
}
}
function callUpdatedHooks(queue: Watcher[]) {
let i = queue.length
while (i--) {
const watcher = queue[i]
const vm = watcher.vm
if (vm && vm._watcher === watcher && vm._isMounted && !vm._isDestroyed) {
callHook(vm, 'updated')
}
}
}
export function queueActivatedComponent(vm: Component) {
vm._inactive = false
activatedChildren.push(vm)
}
function callActivatedHooks(queue) {
for (let i = 0; i < queue.length; i++) {
queue[i]._inactive = true
activateChildComponent(queue[i], true )
}
}
export function queueWatcher(watcher: Watcher) {
const id = watcher.id
if (has[id] != null) {
return
}
if (watcher === Dep.target && watcher.noRecurse) {
return
}
has[id] = true
if (!flushing) {
queue.push(watcher)
} else {
let i = queue.length - 1
while (i > index && queue[i].id > watcher.id) {
i--
}
queue.splice(i + 1, 0, watcher)
}
if (!waiting) {
waiting = true
if (__DEV__ && !config.async) {
flushSchedulerQueue()
return
}
nextTick(flushSchedulerQueue)
}
}
