// Central content + links for the landing site.
// Feature copy mirrors the plugin descriptor
// (community/plugins/agent-workbench/plugin/resources/META-INF/plugin.xml).

export const MARKETPLACE_URL =
  'https://plugins.jetbrains.com/plugin/30926-agent-workbench'
export const GITHUB_URL =
  'https://github.com/JetBrains/intellij-community/tree/master/plugins/agent-workbench'
export const VENDOR = 'Vladimir Krivosheev'

export const TAGLINE =
  'Run and manage AI coding agents — Codex, Claude, Junie, and more — directly inside your IDE.'

export interface Feature {
  /** Display title for the card. */
  title: string
  /** Short caption baked into the placeholder image. */
  caption: string
  /** One-line description. */
  blurb: string
  /** Matches the file name under public/screenshots/<slug>.svg. */
  slug: string
  /** Flagship features get a full card with a placeholder screenshot. */
  featured?: boolean
}

export const FEATURES: Feature[] = [
  {
    title: 'Quick Launch',
    caption: 'Global Prompt',
    blurb:
      'Press Ctrl Ctrl anywhere to start a new task or continue an existing one. Your selection, file, and symbol are attached automatically.',
    slug: 'global-prompt',
    featured: true,
  },
  {
    title: 'Launch Profiles',
    caption: 'Launch Profiles',
    blurb:
      'Bundle provider, model, reasoning effort, and launch mode into presets — Standard, Full Auto, Brave Mode, or your own — and pick a cheaper, faster, or more careful setup per task without changing your default.',
    slug: 'launch-profiles',
    featured: true,
  },
  {
    title: 'AI Review',
    caption: 'AI Review',
    blurb:
      'Hold Alt / Option and press Ctrl twice to review local changes or commits with AI — catching bugs, security issues, and style problems before they reach the main branch.',
    slug: 'ai-review',
  },
  {
    title: 'All Sessions in One Place',
    caption: 'Agent Threads',
    blurb:
      'Browse, reopen, and resume AI sessions across providers, grouped by project.',
    slug: 'agent-threads',
    featured: true,
  },
  {
    title: 'Terminal Sessions',
    caption: 'Terminal Sessions',
    blurb:
      'Open regular IDE terminal shells from the same launcher and manage them alongside your agent threads — reopen, rename, and archive them, and each restores at its last working directory.',
    slug: 'terminal-sessions',
  },
  {
    title: 'Persistent Chat Tabs',
    caption: 'Chat Tabs',
    blurb:
      'Each session opens in its own editor tab and survives IDE restarts.',
    slug: 'chat-tabs',
  },
  {
    title: 'Separate Window Mode',
    caption: 'Dedicated Frame',
    blurb:
      'Run AI chat in a dedicated frame so you can work on code and manage tasks side by side.',
    slug: 'dedicated-frame',
    featured: true,
  },
  {
    title: 'Automatic Context',
    caption: 'Automatic Context',
    blurb:
      'Editor selections, VCS changes, project-view selections, and test results are collected and sent with your prompt so the agent has what it needs.',
    slug: 'automatic-context',
  },
  {
    title: 'Context from UI',
    caption: 'Context from UI',
    blurb:
      'Click any IDE component — tool window, editor, panel — to screenshot it and attach the image as visual context.',
    slug: 'context-from-ui',
  },
  {
    title: 'Context from Screen',
    caption: 'Context from Screen',
    blurb:
      'Drag to select any screen area and capture it as a screenshot to share with the agent, even outside the IDE.',
    slug: 'context-from-screen',
  },
]

export interface Provider {
  name: string
  /** File name under public/icons/<icon>. */
  icon: string
}

export const PROVIDERS: Provider[] = [
  { name: 'Codex', icon: 'codex.svg' },
  { name: 'Claude', icon: 'claude.svg' },
  { name: 'Junie', icon: 'junie.svg' },
  { name: 'Pi', icon: 'pi.svg' },
]
